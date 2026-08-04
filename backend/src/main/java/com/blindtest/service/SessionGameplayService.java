package com.blindtest.service;

import com.blindtest.model.game.AnswerMatchResult;
import com.blindtest.model.game.AnswerMatcher;
import com.blindtest.model.game.GameAnswerSnapshot;
import com.blindtest.model.game.GameAnswerType;
import com.blindtest.model.game.GameProgression;
import com.blindtest.model.game.GameStateMachine;
import com.blindtest.model.game.GameStateSnapshot;
import com.blindtest.model.game.GameSummary;
import com.blindtest.model.game.GameTransitionException;
import com.blindtest.model.game.RoundCompletionTransition;
import com.blindtest.model.game.RoundScoringWindow;
import com.blindtest.model.game.RoundStateMachine;
import com.blindtest.model.game.RoundStateSnapshot;
import com.blindtest.model.game.RoundStatus;
import com.blindtest.model.game.SecondaryDiscoveryOutcome;
import com.blindtest.model.game.SecondaryDiscoveryResult;
import com.blindtest.model.game.SecondaryDiscoveryTracker;
import com.blindtest.model.game.TitleScoreCalculator;
import com.blindtest.model.session.ActiveSessionSnapshot;
import com.blindtest.model.session.SessionId;
import com.blindtest.model.session.SessionParticipant;
import com.blindtest.service.port.MediaStorage;
import com.blindtest.service.port.SessionIdentityGenerator;
import com.blindtest.service.port.SessionTaskScheduler;
import com.blindtest.store.SessionStore;
import com.blindtest.store.SessionUpdateOutcome;
import com.blindtest.websocket.SessionEventPublisher;
import com.blindtest.websocket.protocol.AnswerRejectionReason;
import com.blindtest.websocket.protocol.PrivateSessionEvent;
import com.blindtest.websocket.protocol.PrivateSessionEventPayload;
import com.blindtest.websocket.protocol.PrivateSessionEventType;
import com.blindtest.websocket.protocol.PublicSessionEvent;
import com.blindtest.websocket.protocol.PublicSessionEventPayload;
import com.blindtest.websocket.protocol.PublicSessionEventType;
import com.blindtest.websocket.protocol.SessionCommand;
import com.blindtest.websocket.protocol.SessionCommandType;
import com.blindtest.websocket.protocol.SessionEventMetadata;
import com.blindtest.websocket.protocol.SessionProtocol;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Serializes gameplay commands and authoritative timers into atomic active-session versions. */
@Service
public class SessionGameplayService {

    static final Duration REVEAL_DURATION = Duration.ofSeconds(4);
    private static final int LOCK_STRIPES = 64;

    private final SessionStore sessionStore;
    private final SessionTaskScheduler scheduler;
    private final MediaStorage mediaStorage;
    private final SessionEventPublisher eventPublisher;
    private final SessionIdentityGenerator identityGenerator;
    private final Clock clock;
    private final GameStateMachine gameStateMachine = new GameStateMachine(
            new RoundStateMachine(new TitleScoreCalculator()));
    private final AnswerMatcher answerMatcher = new AnswerMatcher();
    private final SecondaryDiscoveryTracker discoveryTracker = new SecondaryDiscoveryTracker();
    private final Object[] locks = new Object[LOCK_STRIPES];

    /** Wires active state, scheduling, private media, publishing, identity, and time boundaries. */
    public SessionGameplayService(
            SessionStore sessionStore,
            SessionTaskScheduler scheduler,
            MediaStorage mediaStorage,
            SessionEventPublisher eventPublisher,
            SessionIdentityGenerator identityGenerator,
            Clock clock) {
        this.sessionStore = sessionStore;
        this.scheduler = scheduler;
        this.mediaStorage = mediaStorage;
        this.eventPublisher = eventPublisher;
        this.identityGenerator = identityGenerator;
        this.clock = clock;
        Arrays.setAll(locks, ignored -> new Object());
    }

    /** Applies one authenticated STOMP command to its canonical active session. */
    public boolean handle(UUID rawSessionId, UUID accountId, SessionCommand command) {
        Objects.requireNonNull(rawSessionId, "sessionId is required");
        Objects.requireNonNull(accountId, "accountId is required");
        Objects.requireNonNull(command, "command is required");
        SessionId sessionId = new SessionId(rawSessionId);
        synchronized (lockFor(sessionId)) {
            ActiveSessionSnapshot session = find(sessionId);
            SessionParticipant participant = session == null ? null : participant(session, accountId);
            return session != null && participant != null
                    && handleAuthenticated(session, participant, accountId, command);
        }
    }

    private boolean handleAuthenticated(
            ActiveSessionSnapshot session,
            SessionParticipant participant,
            UUID accountId,
            SessionCommand command) {
        if (command.type() == SessionCommandType.REQUEST_SNAPSHOT) {
            publishSnapshot(session, participant, accountId, command.commandId());
            return true;
        }
        if (session.processedCommands().contains(command.commandId())) {
            reject(session, participant, accountId, command, AnswerRejectionReason.DUPLICATE_COMMAND);
            return true;
        }
        if (command.expectedSessionVersion() != session.version()) {
            reject(
                    session,
                    participant,
                    accountId,
                    command,
                    AnswerRejectionReason.STALE_SESSION_VERSION);
            return true;
        }
        return switch (command.type()) {
            case START_GAME -> startGame(session, participant, accountId, command);
            case SUBMIT_ANSWER -> submitAnswer(session, participant, accountId, command);
            case SKIP_ROUND -> skipRound(session, participant, accountId, command);
            case ADVANCE_ROUND -> advanceRound(session, participant, accountId, command);
            case REQUEST_SNAPSHOT -> throw new IllegalStateException("snapshot handled earlier");
            default -> throw new IllegalStateException("Unknown gameplay command");
        };
    }

    private boolean startGame(
            ActiveSessionSnapshot session,
            SessionParticipant participant,
            UUID accountId,
            SessionCommand command) {
        if (!isOwner(session, participant)) {
            commandError(session, participant, accountId, command, "SESSION_OWNER_REQUIRED",
                    "Only the session owner can start the game.");
            return true;
        }
        try {
            GameStateSnapshot started = gameStateMachine.start(session.gameState());
            ActiveSessionSnapshot persisted = nextForCommand(session, started, command, clock.instant());
            if (!persist(session, persisted)) {
                staleAfterConflict(session, participant, accountId, command);
                return true;
            }
            publishPublic(
                    persisted,
                    PublicSessionEventType.ROUND_PREPARING,
                    new PublicSessionEventPayload.RoundPreparing(
                            started.currentRoundIndex(), totalRounds(started)));
            startPreparedRound(persisted.sessionId(), started.currentRoundIndex());
            return true;
        } catch (GameTransitionException exception) {
            invalidState(session, participant, accountId, command);
            return true;
        }
    }

    private boolean submitAnswer(
            ActiveSessionSnapshot session,
            SessionParticipant participant,
            UUID accountId,
            SessionCommand command) {
        RoundStateSnapshot round = playingRound(session);
        if (round == null) {
            invalidState(session, participant, accountId, command);
            return true;
        }
        Instant acceptedAt = clock.instant();
        AnswerMatchResult match = answerMatcher.match(command.answer(), round.track().answers());
        Optional<GameAnswerType> matchedType = match.matchedType();
        if (matchedType.filter(type -> type == GameAnswerType.TITLE).isPresent()) {
            return resolveTitle(session, participant, accountId, command, match, acceptedAt);
        }
        SecondaryDiscoveryResult discovery = discoveryTracker.route(
                session.discoveries(), participant.participantId(), match);
        ActiveSessionSnapshot persisted = session.next(
                session.gameState(),
                discovery.state(),
                session.processedCommands().record(command.commandId()),
                acceptedAt,
                session.expiresAt());
        if (!persist(session, persisted)) {
            staleAfterConflict(session, participant, accountId, command);
            return true;
        }
        if (discovery.outcome() == SecondaryDiscoveryOutcome.DISCOVERED) {
            GameAnswerType type = discovery.discoveredType().orElseThrow();
            publishPrivate(
                    persisted,
                    participant,
                    accountId,
                    Optional.of(command.commandId()),
                    PrivateSessionEventType.SECONDARY_MATCH,
                    new PrivateSessionEventPayload.SecondaryMatch(
                            type, displayValue(round.track().answers(), type), false));
        } else {
            AnswerRejectionReason reason = discovery.outcome()
                    == SecondaryDiscoveryOutcome.ALREADY_FOUND
                    ? AnswerRejectionReason.ALREADY_FOUND
                    : AnswerRejectionReason.WRONG_ANSWER;
            answerRejected(persisted, participant, accountId, command.commandId(), reason);
        }
        return true;
    }

    private boolean resolveTitle(
            ActiveSessionSnapshot session,
            SessionParticipant participant,
            UUID accountId,
            SessionCommand command,
            AnswerMatchResult match,
            Instant acceptedAt) {
        try {
            GameStateSnapshot revealed = gameStateMachine.resolveTitle(
                    session.gameState(), match, acceptedAt);
            ActiveSessionSnapshot persisted = nextForCommand(
                    session, revealed, command, acceptedAt);
            if (!persist(session, persisted)) {
                staleAfterConflict(session, participant, accountId, command);
                return true;
            }
            publishRevealAndSchedule(persisted);
            return true;
        } catch (GameTransitionException exception) {
            invalidState(session, participant, accountId, command);
            return true;
        }
    }

    private boolean skipRound(
            ActiveSessionSnapshot session,
            SessionParticipant participant,
            UUID accountId,
            SessionCommand command) {
        if (!isOwner(session, participant)) {
            commandError(session, participant, accountId, command, "SESSION_OWNER_REQUIRED",
                    "Only the session owner can skip the round.");
            return true;
        }
        try {
            Instant skippedAt = clock.instant();
            GameStateSnapshot revealed = gameStateMachine.skipRound(
                    session.gameState(), skippedAt);
            ActiveSessionSnapshot persisted = nextForCommand(
                    session, revealed, command, skippedAt);
            if (!persist(session, persisted)) {
                staleAfterConflict(session, participant, accountId, command);
                return true;
            }
            publishRevealAndSchedule(persisted);
            return true;
        } catch (GameTransitionException exception) {
            invalidState(session, participant, accountId, command);
            return true;
        }
    }

    private boolean advanceRound(
            ActiveSessionSnapshot session,
            SessionParticipant participant,
            UUID accountId,
            SessionCommand command) {
        if (!isOwner(session, participant)) {
            commandError(session, participant, accountId, command, "SESSION_OWNER_REQUIRED",
                    "Only the session owner can advance the round.");
            return true;
        }
        RoundStateSnapshot round = currentRound(session);
        if (round == null || round.status() != RoundStatus.REVEALED) {
            invalidState(session, participant, accountId, command);
            return true;
        }
        Instant now = clock.instant();
        RoundCompletionTransition completion = gameStateMachine.completeRound(
                session.gameState(), now);
        ActiveSessionSnapshot persisted = nextForCommand(
                session, completion.game(), command, now);
        if (!persist(session, persisted)) {
            staleAfterConflict(session, participant, accountId, command);
            return true;
        }
        publishProgression(persisted, completion.progression());
        return true;
    }

    private boolean startPreparedRound(SessionId sessionId, int roundIndex) {
        ActiveSessionSnapshot session = find(sessionId);
        if (session == null || session.gameState().currentRoundIndex() != roundIndex) {
            return false;
        }
        RoundStateSnapshot round = currentRound(session);
        if (round == null || round.status() != RoundStatus.PREPARING) {
            return false;
        }
        Instant startsAt = clock.instant();
        Duration duration = Duration.ofSeconds(
                session.configuration().maximumRoundDurationSeconds());
        RoundScoringWindow window = new RoundScoringWindow(startsAt.plus(duration), duration);
        GameStateSnapshot playing = gameStateMachine.startRound(session.gameState(), window);
        RoundStateSnapshot startedRound = playing.currentRound().orElseThrow();
        URI playbackUrl = resolvePlaybackUrl(startedRound);
        ActiveSessionSnapshot persisted = session.next(
                playing,
                session.discoveries(),
                session.processedCommands(),
                startsAt,
                session.expiresAt());
        if (!persist(session, persisted)) {
            return false;
        }
        publishPublic(
                persisted,
                PublicSessionEventType.ROUND_STARTED,
                new PublicSessionEventPayload.RoundStarted(
                        roundIndex,
                        totalRounds(playing),
                        window.startsAt(),
                        window.deadline(),
                        round.track().playback().excerptStartSeconds(),
                        Optional.of(playbackUrl)));
        scheduleDeadline(persisted, roundIndex, window.deadline());
        return true;
    }

    private void scheduleDeadline(
            ActiveSessionSnapshot session,
            int roundIndex,
            Instant deadline) {
        scheduler.schedule(deadline, () -> onDeadline(session.sessionId(), roundIndex, deadline));
    }

    private boolean onDeadline(SessionId sessionId, int roundIndex, Instant deadline) {
        synchronized (lockFor(sessionId)) {
            ActiveSessionSnapshot session = find(sessionId);
            if (session == null || session.gameState().currentRoundIndex() != roundIndex) {
                return false;
            }
            RoundStateSnapshot round = currentRound(session);
            if (round == null || round.status() != RoundStatus.PLAYING
                    || round.scoringWindow().filter(window -> window.deadline().equals(deadline)).
                    isEmpty()) {
                return false;
            }
            Instant now = clock.instant();
            if (now.isBefore(deadline)) {
                scheduleDeadline(session, roundIndex, deadline);
                return true;
            }
            GameStateSnapshot timedOut = gameStateMachine.timeoutRound(session.gameState(), now);
            ActiveSessionSnapshot persisted = session.next(
                    timedOut,
                    session.discoveries(),
                    session.processedCommands(),
                    now,
                    session.expiresAt());
            if (persist(session, persisted)) {
                publishRevealAndSchedule(persisted);
                return true;
            }
            return false;
        }
    }

    private void publishRevealAndSchedule(ActiveSessionSnapshot session) {
        RoundStateSnapshot round = currentRound(session);
        if (round == null || round.status() != RoundStatus.REVEALED) {
            throw new IllegalStateException("revealed round is required");
        }
        Instant advanceAt = round.revealedAt().orElseThrow().plus(REVEAL_DURATION);
        int totalScore = totalScore(session.gameState());
        if (round.scoreState().titleAwarded()) {
            publishPublic(
                    session,
                    PublicSessionEventType.SCORE_UPDATED,
                    new PublicSessionEventPayload.ScoreUpdated(
                            session.ownerParticipantId().value(),
                            round.scoreState().totalPoints(),
                            totalScore));
        }
        GameAnswerSnapshot answers = round.track().answers();
        publishPublic(
                session,
                PublicSessionEventType.ROUND_REVEALED,
                new PublicSessionEventPayload.RoundRevealed(
                        session.gameState().currentRoundIndex(),
                        totalRounds(session.gameState()),
                        round.outcome().orElseThrow(),
                        answers.title(),
                        answers.artist(),
                        Optional.ofNullable(answers.composer()),
                        Optional.ofNullable(answers.album()),
                        round.scoreState().totalPoints(),
                        totalScore,
                        advanceAt));
        scheduler.schedule(advanceAt, () -> onAdvance(
                session.sessionId(),
                session.gameState().currentRoundIndex(),
                round.revealedAt().orElseThrow()));
    }

    private boolean onAdvance(SessionId sessionId, int roundIndex, Instant revealedAt) {
        synchronized (lockFor(sessionId)) {
            ActiveSessionSnapshot session = find(sessionId);
            if (session == null || session.gameState().currentRoundIndex() != roundIndex) {
                return false;
            }
            RoundStateSnapshot round = currentRound(session);
            if (round == null || round.status() != RoundStatus.REVEALED
                    || round.revealedAt().filter(revealedAt::equals).isEmpty()) {
                return false;
            }
            Instant advanceAt = revealedAt.plus(REVEAL_DURATION);
            Instant now = clock.instant();
            if (now.isBefore(advanceAt)) {
                scheduler.schedule(advanceAt, () -> onAdvance(sessionId, roundIndex, revealedAt));
                return true;
            }
            RoundCompletionTransition completion = gameStateMachine.completeRound(
                    session.gameState(), now);
            ActiveSessionSnapshot persisted = session.next(
                    completion.game(),
                    session.discoveries(),
                    session.processedCommands(),
                    now,
                    session.expiresAt());
            if (persist(session, persisted)) {
                publishProgression(persisted, completion.progression());
                return true;
            }
            return false;
        }
    }

    private void publishProgression(
            ActiveSessionSnapshot session,
            GameProgression progression) {
        if (progression == GameProgression.GAME_FINISHED) {
            GameSummary summary = session.gameState().finalSummary().orElseThrow();
            publishPublic(
                    session,
                    PublicSessionEventType.GAME_FINISHED,
                    new PublicSessionEventPayload.GameFinished(
                            summary.totalScore(),
                            summary.totalRounds(),
                            summary.titlesFound(),
                            summary.skippedRounds(),
                            summary.timedOutRounds()));
            return;
        }
        int roundIndex = session.gameState().currentRoundIndex();
        publishPublic(
                session,
                PublicSessionEventType.ROUND_PREPARING,
                new PublicSessionEventPayload.RoundPreparing(
                        roundIndex, totalRounds(session.gameState())));
        startPreparedRound(session.sessionId(), roundIndex);
    }

    private void publishSnapshot(
            ActiveSessionSnapshot session,
            SessionParticipant participant,
            UUID accountId,
            UUID commandId) {
        GameStateSnapshot game = session.gameState();
        Optional<PrivateSessionEventPayload.CurrentRound> current = game.currentRound().
                map(round -> currentRoundPayload(game.currentRoundIndex(), round));
        List<GameAnswerType> discoveries = session.discoveries().
                projectFor(participant.participantId()).
                discoveredTypes().stream().
                sorted(Comparator.comparingInt(Enum::ordinal)).
                toList();
        publishPrivate(
                session,
                participant,
                accountId,
                Optional.of(commandId),
                PrivateSessionEventType.CURRENT_SNAPSHOT,
                new PrivateSessionEventPayload.CurrentSnapshot(
                        game.status(),
                        game.currentRoundIndex(),
                        totalRounds(game),
                        totalScore(game),
                        current,
                        discoveries,
                        session.expiresAt()));
    }

    private PrivateSessionEventPayload.CurrentRound currentRoundPayload(
            int roundIndex,
            RoundStateSnapshot round) {
        Optional<Instant> startsAt = round.scoringWindow().map(RoundScoringWindow::startsAt);
        Optional<Instant> deadline = round.scoringWindow().map(RoundScoringWindow::deadline);
        boolean revealed = round.status() == RoundStatus.REVEALED;
        Optional<Instant> advanceAt = revealed
                ? round.revealedAt().map(value -> value.plus(REVEAL_DURATION))
                : Optional.empty();
        Optional<URI> playbackUrl = round.status() == RoundStatus.PLAYING
                ? Optional.of(resolvePlaybackUrl(round))
                : Optional.empty();
        return new PrivateSessionEventPayload.CurrentRound(
                roundIndex,
                round.status(),
                startsAt,
                deadline,
                playbackUrl,
                round.playbackWindow().map(window -> window.excerptStartSeconds()),
                round.outcome(),
                revealed ? Optional.of(round.track().answers().title()) : Optional.empty(),
                revealed ? Optional.of(round.track().answers().artist()) : Optional.empty(),
                revealed
                        ? Optional.ofNullable(round.track().answers().composer())
                        : Optional.empty(),
                revealed
                        ? Optional.ofNullable(round.track().answers().album())
                        : Optional.empty(),
                advanceAt,
                round.scoreState().totalPoints());
    }

    private URI resolvePlaybackUrl(RoundStateSnapshot round) {
        var window = round.playbackWindow().orElseThrow();
        Duration minimumValidity = Duration.between(
                clock.instant(), window.minimumUrlExpiresAt());
        if (minimumValidity.isNegative()) {
            minimumValidity = Duration.ZERO;
        }
        return mediaStorage.presignedGetUrl(
                round.track().media().objectKey(), minimumValidity);
    }

    private ActiveSessionSnapshot nextForCommand(
            ActiveSessionSnapshot session,
            GameStateSnapshot game,
            SessionCommand command,
            Instant now) {
        return session.next(
                game,
                session.discoveries(),
                session.processedCommands().record(command.commandId()),
                now,
                session.expiresAt());
    }

    private void reject(
            ActiveSessionSnapshot session,
            SessionParticipant participant,
            UUID accountId,
            SessionCommand command,
            AnswerRejectionReason reason) {
        if (command.type() == SessionCommandType.SUBMIT_ANSWER) {
            answerRejected(session, participant, accountId, command.commandId(), reason);
        } else {
            commandError(
                    session,
                    participant,
                    accountId,
                    command,
                    reason.name(),
                    "The gameplay command was not accepted.");
        }
    }

    private void invalidState(
            ActiveSessionSnapshot session,
            SessionParticipant participant,
            UUID accountId,
            SessionCommand command) {
        reject(
                session,
                participant,
                accountId,
                command,
                AnswerRejectionReason.INVALID_ROUND_STATE);
    }

    private void staleAfterConflict(
            ActiveSessionSnapshot observed,
            SessionParticipant participant,
            UUID accountId,
            SessionCommand command) {
        ActiveSessionSnapshot latest = find(observed.sessionId());
        if (latest != null) {
            AnswerRejectionReason reason = latest.processedCommands().contains(command.commandId())
                    ? AnswerRejectionReason.DUPLICATE_COMMAND
                    : AnswerRejectionReason.STALE_SESSION_VERSION;
            reject(latest, participant, accountId, command, reason);
        }
    }

    private void answerRejected(
            ActiveSessionSnapshot session,
            SessionParticipant participant,
            UUID accountId,
            UUID commandId,
            AnswerRejectionReason reason) {
        publishPrivate(
                session,
                participant,
                accountId,
                Optional.of(commandId),
                PrivateSessionEventType.ANSWER_REJECTED,
                new PrivateSessionEventPayload.AnswerRejected(reason, Optional.empty()));
    }

    private void commandError(
            ActiveSessionSnapshot session,
            SessionParticipant participant,
            UUID accountId,
            SessionCommand command,
            String code,
            String message) {
        publishPrivate(
                session,
                participant,
                accountId,
                Optional.of(command.commandId()),
                PrivateSessionEventType.ERROR,
                new PrivateSessionEventPayload.Error(code, message, true));
    }

    private void publishPublic(
            ActiveSessionSnapshot session,
            PublicSessionEventType type,
            PublicSessionEventPayload payload) {
        eventPublisher.publishPublic(new PublicSessionEvent(metadata(session), type, payload));
    }

    private void publishPrivate(
            ActiveSessionSnapshot session,
            SessionParticipant participant,
            UUID accountId,
            Optional<UUID> commandId,
            PrivateSessionEventType type,
            PrivateSessionEventPayload payload) {
        eventPublisher.publishPrivate(accountId, new PrivateSessionEvent(
                metadata(session), participant.participantId().value(), commandId, type, payload));
    }

    private SessionEventMetadata metadata(ActiveSessionSnapshot session) {
        return new SessionEventMetadata(
                SessionProtocol.VERSION,
                identityGenerator.nextId(),
                session.sessionId().value(),
                session.version(),
                clock.instant());
    }

    private boolean persist(
            ActiveSessionSnapshot previous,
            ActiveSessionSnapshot replacement) {
        return sessionStore.compareAndSet(
                previous.sessionId(),
                previous.version(),
                replacement,
                clock.instant()) == SessionUpdateOutcome.UPDATED;
    }

    private ActiveSessionSnapshot find(SessionId sessionId) {
        return sessionStore.find(sessionId, clock.instant()).orElse(null);
    }

    private SessionParticipant participant(
            ActiveSessionSnapshot session,
            UUID accountId) {
        return session.participants().stream().
                filter(value -> value.accountId().filter(accountId::equals).isPresent()).
                findFirst().orElse(null);
    }

    private boolean isOwner(
            ActiveSessionSnapshot session,
            SessionParticipant participant) {
        return session.ownerParticipantId().equals(participant.participantId());
    }

    private RoundStateSnapshot playingRound(ActiveSessionSnapshot session) {
        RoundStateSnapshot round = currentRound(session);
        return round != null && round.status() == RoundStatus.PLAYING ? round : null;
    }

    private RoundStateSnapshot currentRound(ActiveSessionSnapshot session) {
        return session.gameState().currentRound().orElse(null);
    }

    private int totalRounds(GameStateSnapshot game) {
        return game.trackSelection().tracks().size();
    }

    private int totalScore(GameStateSnapshot game) {
        int completed = game.completedRounds().stream().
                mapToInt(round -> round.scoreState().totalPoints()).
                sum();
        int current = game.currentRound().
                map(round -> round.scoreState().totalPoints()).
                orElse(0);
        return completed + current;
    }

    private String displayValue(GameAnswerSnapshot answers, GameAnswerType type) {
        return switch (type) {
            case ARTIST -> answers.artist();
            case COMPOSER -> answers.composer();
            case ALBUM -> answers.album();
            case TITLE -> throw new IllegalArgumentException("secondary answer type is required");
        };
    }

    private Object lockFor(SessionId sessionId) {
        return locks[Math.floorMod(sessionId.hashCode(), locks.length)];
    }
}
