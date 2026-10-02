package com.blindtest.service;

import com.blindtest.model.entity.TrackCovers;
import com.blindtest.model.game.AnswerMatchResult;
import com.blindtest.model.game.AnswerMatcher;
import com.blindtest.model.game.GameAnswerSnapshot;
import com.blindtest.model.game.GameAnswerType;
import com.blindtest.model.game.GameParticipantId;
import com.blindtest.model.game.GameProgression;
import com.blindtest.model.game.GameStateMachine;
import com.blindtest.model.game.GameStateSnapshot;
import com.blindtest.model.game.GameSummary;
import com.blindtest.model.game.GameTransitionException;
import com.blindtest.model.game.RoundCompletionTransition;
import com.blindtest.model.game.RoundPlaybackWindow;
import com.blindtest.model.game.RoundScoringWindow;
import com.blindtest.model.game.RoundStateMachine;
import com.blindtest.model.game.RoundStateSnapshot;
import com.blindtest.model.game.RoundStatus;
import com.blindtest.model.game.SecondaryDiscoveryOutcome;
import com.blindtest.model.game.SecondaryDiscoveryResult;
import com.blindtest.model.game.SecondaryDiscoveryState;
import com.blindtest.model.game.SecondaryDiscoveryTracker;
import com.blindtest.model.game.SkipVoteRule;
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
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Serializes gameplay commands and authoritative timers into atomic active-session versions. */
@Service
public class SessionGameplayService {

    static final Duration REVEAL_DURATION = Duration.ofSeconds(4);
    /** Multiplayer rounds start slightly in the future so every client begins playback together. */
    static final Duration MULTIPLAYER_ROUND_LEAD = Duration.ofMillis(1500);
    private static final int LOCK_STRIPES = 64;

    private final SessionStore sessionStore;
    private final SessionTaskScheduler scheduler;
    private final SessionExpirationPolicy expirationPolicy;
    private final MediaStorage mediaStorage;
    private final GameplayCommandRateLimiter rateLimiter;
    private final GameHistoryCompletionService historyCompletionService;
    private final SessionEventPublisher eventPublisher;
    private final SessionIdentityGenerator identityGenerator;
    private final GameStateMachine gameStateMachine = new GameStateMachine(
            new RoundStateMachine(new TitleScoreCalculator()));
    private final AnswerMatcher answerMatcher = new AnswerMatcher();
    private final SecondaryDiscoveryTracker discoveryTracker = new SecondaryDiscoveryTracker();
    private final Object[] locks = new Object[LOCK_STRIPES];

    /** Wires active state, scheduling, private media, publishing, identity, and time boundaries. */
    public SessionGameplayService(
            SessionStore sessionStore,
            SessionTaskScheduler scheduler,
            SessionExpirationPolicy expirationPolicy,
            MediaStorage mediaStorage,
            GameplayCommandRateLimiter rateLimiter,
            GameHistoryCompletionService historyCompletionService,
            SessionEventPublisher eventPublisher,
            SessionIdentityGenerator identityGenerator) {
        this.sessionStore = sessionStore;
        this.scheduler = scheduler;
        this.expirationPolicy = expirationPolicy;
        this.mediaStorage = mediaStorage;
        this.rateLimiter = rateLimiter;
        this.historyCompletionService = historyCompletionService;
        this.eventPublisher = eventPublisher;
        this.identityGenerator = identityGenerator;
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
            if (session == null) {
                rateLimiter.forget(sessionId);
                return false;
            }
            SessionParticipant participant = participant(session, accountId);
            if (participant == null) {
                return false;
            }
            var rateDecision = rateLimiter.checkSession(
                    sessionId, expirationPolicy.instant(), session.participants().size());
            if (rateDecision.action()
                    == GameplayCommandRateLimiter.RateLimitAction.DROP_SILENTLY) {
                return true;
            }
            if (rateDecision.action()
                    == GameplayCommandRateLimiter.RateLimitAction.REJECT_SESSION_WITH_FEEDBACK) {
                commandError(
                        session,
                        participant,
                        accountId,
                        command,
                        "SESSION_RATE_LIMITED",
                        "Too many gameplay commands. Try again shortly.");
                return true;
            }
            return handleAuthenticated(session, participant, accountId, command);
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
        if (!isFresh(session, command)) {
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
            ActiveSessionSnapshot persisted = nextForCommand(
                    session, started, command, expirationPolicy.instant());
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
        Instant acceptedAt = expirationPolicy.instant();
        if (answerRateLimited(session, participant, accountId, command, acceptedAt)) {
            return true;
        }
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
                expirationPolicy.expiresAt(session.gameState().status(), acceptedAt));
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

    private boolean answerRateLimited(
            ActiveSessionSnapshot session,
            SessionParticipant participant,
            UUID accountId,
            SessionCommand command,
            Instant acceptedAt) {
        var rateDecision = rateLimiter.checkAnswer(
                session.sessionId(), participant.participantId(), acceptedAt);
        if (rateDecision.action()
                == GameplayCommandRateLimiter.RateLimitAction.DROP_SILENTLY) {
            return true;
        }
        if (rateDecision.action()
                == GameplayCommandRateLimiter.RateLimitAction.REJECT_WITH_COOLDOWN) {
            answerRejected(
                    session,
                    participant,
                    accountId,
                    command.commandId(),
                    AnswerRejectionReason.COOLDOWN,
                    rateDecision.retryAt());
            return true;
        }
        return false;
    }

    private boolean resolveTitle(
            ActiveSessionSnapshot session,
            SessionParticipant participant,
            UUID accountId,
            SessionCommand command,
            AnswerMatchResult match,
            Instant acceptedAt) {
        if (currentRound(session).scoreState().titleAwarded(participant.participantId())) {
            answerRejected(session, participant, accountId, command.commandId(),
                    AnswerRejectionReason.ALREADY_FOUND);
            return true;
        }
        try {
            GameStateSnapshot awarded = gameStateMachine.awardTitle(
                    session.gameState(),
                    participant.participantId(),
                    match,
                    acceptedAt,
                    activeParticipants(session));
            ActiveSessionSnapshot persisted = nextForCommand(
                    session, awarded, command, acceptedAt);
            if (!persist(session, persisted)) {
                staleAfterConflict(session, participant, accountId, command);
                return true;
            }
            if (persisted.isMultiplayer()) {
                // Announces who found the title, never the answer itself.
                publishPublic(
                        persisted,
                        PublicSessionEventType.SCORE_UPDATED,
                        new PublicSessionEventPayload.ScoreUpdated(
                                participant.participantId().value(),
                                currentRound(persisted).scoreState().
                                        pointsFor(participant.participantId()),
                                persisted.gameState().totalScoreFor(participant.participantId())));
            }
            if (currentRound(persisted).status() == RoundStatus.REVEALED) {
                publishRevealAndSchedule(persisted);
            }
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
        try {
            Instant skippedAt = expirationPolicy.instant();
            GameStateSnapshot voted = gameStateMachine.voteSkip(
                    session.gameState(),
                    participant.participantId(),
                    skippedAt,
                    activeParticipants(session));
            ActiveSessionSnapshot persisted = nextForCommand(
                    session, voted, command, skippedAt);
            if (!persist(session, persisted)) {
                staleAfterConflict(session, participant, accountId, command);
                return true;
            }
            RoundStateSnapshot round = currentRound(persisted);
            if (round.status() == RoundStatus.REVEALED) {
                publishRevealAndSchedule(persisted);
            } else {
                Set<GameParticipantId> active = activeParticipants(persisted);
                publishPublic(
                        persisted,
                        PublicSessionEventType.SKIP_VOTE_UPDATED,
                        new PublicSessionEventPayload.SkipVoteUpdated(
                                Math.toIntExact(active.stream().
                                        filter(round.scoreState()::skipVoted).count()),
                                SkipVoteRule.requiredVotes(active.size())));
            }
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
        Instant now = expirationPolicy.instant();
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
        Instant now = expirationPolicy.instant();
        Instant startsAt = session.isMultiplayer() ? now.plus(MULTIPLAYER_ROUND_LEAD) : now;
        Duration duration = Duration.ofSeconds(
                session.configuration().maximumRoundDurationSeconds());
        RoundScoringWindow window = new RoundScoringWindow(startsAt.plus(duration), duration);
        GameStateSnapshot playing = gameStateMachine.startRound(session.gameState(), window);
        RoundStateSnapshot startedRound = playing.currentRound().orElseThrow();
        URI playbackUrl = resolvePlaybackUrl(startedRound);
        RoundPlaybackWindow playbackWindow = startedRound.playbackWindow().orElseThrow();
        // Secondary discoveries belong to one round: every track starts with none found.
        ActiveSessionSnapshot persisted = session.next(
                playing,
                SecondaryDiscoveryState.empty(),
                session.processedCommands(),
                now,
                expirationPolicy.expiresAt(playing.status(), now)).
                markRoundStarted();
        if (!persist(session, persisted)) {
            return false;
        }
        scheduleDeadline(persisted, roundIndex, window.deadline());
        publishPublic(
                persisted,
                PublicSessionEventType.ROUND_STARTED,
                new PublicSessionEventPayload.RoundStarted(
                        roundIndex,
                        totalRounds(playing),
                        window.startsAt(),
                        window.deadline(),
                        playbackWindow.excerptStartSeconds(),
                        playbackWindow.excerptDurationSeconds(),
                        Optional.of(playbackUrl)));
        return true;
    }

    private void scheduleDeadline(
            ActiveSessionSnapshot session,
            int roundIndex,
            Instant deadline) {
        scheduler.schedule(
                session.sessionId(), deadline,
                () -> onDeadline(session.sessionId(), roundIndex, deadline));
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
            Instant now = expirationPolicy.instant();
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
                    expirationPolicy.expiresAt(timedOut.status(), now));
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
        // Schedule before publishing so the advance exists once clients observe the reveal.
        scheduler.schedule(session.sessionId(), advanceAt, () -> onAdvance(
                session.sessionId(),
                session.gameState().currentRoundIndex(),
                round.revealedAt().orElseThrow()));
        int totalScore = totalScore(session.gameState());
        // Multiplayer score changes were already announced as each participant found the title.
        if (!session.isMultiplayer() && round.scoreState().titleAwarded()) {
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
                        round.track().streamingLinks().links(),
                        coverUrl(round),
                        round.scoreState().totalPoints(),
                        totalScore,
                        advanceAt,
                        participantScores(session)));
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
            Instant now = expirationPolicy.instant();
            if (now.isBefore(advanceAt)) {
                scheduler.schedule(
                        sessionId, advanceAt,
                        () -> onAdvance(sessionId, roundIndex, revealedAt));
                return true;
            }
            RoundCompletionTransition completion = gameStateMachine.completeRound(
                    session.gameState(), now);
            ActiveSessionSnapshot persisted = session.next(
                    completion.game(),
                    session.discoveries(),
                    session.processedCommands(),
                    now,
                    expirationPolicy.expiresAt(completion.game().status(), now));
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
                            summary.timedOutRounds(),
                            summary.standings().stream().
                                    map(standing -> new PublicSessionEventPayload.ParticipantResult(
                                            standing.participantId().value(),
                                            displayName(session, standing.participantId()),
                                            standing.totalScore(),
                                            standing.titlesFound(),
                                            standing.rank())).
                                    toList()));
            scheduler.cancelSessionTasks(session.sessionId());
            rateLimiter.forget(session.sessionId());
            historyCompletionService.complete(session);
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
                map(round -> currentRoundPayload(
                        session, participant, game.currentRoundIndex(), round));
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
                        game.totalScoreFor(participant.participantId()),
                        current,
                        discoveries,
                        session.expiresAt(),
                        participantScores(session)));
    }

    private PrivateSessionEventPayload.CurrentRound currentRoundPayload(
            ActiveSessionSnapshot session,
            SessionParticipant participant,
            int roundIndex,
            RoundStateSnapshot round) {
        GameParticipantId recipient = participant.participantId();
        Set<GameParticipantId> active = activeParticipants(session);
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
                round.playbackWindow().map(RoundPlaybackWindow::excerptDurationSeconds),
                round.outcome(),
                revealed ? Optional.of(round.track().answers().title()) : Optional.empty(),
                revealed ? Optional.of(round.track().answers().artist()) : Optional.empty(),
                revealed
                        ? Optional.ofNullable(round.track().answers().composer())
                        : Optional.empty(),
                revealed
                        ? Optional.ofNullable(round.track().answers().album())
                        : Optional.empty(),
                revealed ? round.track().streamingLinks().links() : Map.of(),
                revealed ? coverUrl(round) : Optional.empty(),
                advanceAt,
                round.scoreState().pointsFor(recipient),
                round.scoreState().titleAwarded(recipient),
                round.scoreState().skipVoted(recipient),
                Math.toIntExact(active.stream().filter(round.scoreState()::skipVoted).count()),
                SkipVoteRule.requiredVotes(active.size()));
    }

    private static Optional<String> coverUrl(RoundStateSnapshot round) {
        return Optional.ofNullable(round.track().coverId()).map(TrackCovers::publicPath);
    }

    private URI resolvePlaybackUrl(RoundStateSnapshot round) {
        var window = round.playbackWindow().orElseThrow();
        Duration minimumValidity = Duration.between(
                expirationPolicy.instant(), window.minimumUrlExpiresAt());
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
                expirationPolicy.expiresAt(game.status(), now));
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
        answerRejected(
                session, participant, accountId, commandId, reason, Optional.empty());
    }

    private void answerRejected(
            ActiveSessionSnapshot session,
            SessionParticipant participant,
            UUID accountId,
            UUID commandId,
            AnswerRejectionReason reason,
            Optional<Instant> retryAt) {
        publishPrivate(
                session,
                participant,
                accountId,
                Optional.of(commandId),
                PrivateSessionEventType.ANSWER_REJECTED,
                new PrivateSessionEventPayload.AnswerRejected(reason, retryAt));
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
                expirationPolicy.instant());
    }

    private boolean persist(
            ActiveSessionSnapshot previous,
            ActiveSessionSnapshot replacement) {
        return sessionStore.compareAndSet(
                previous.sessionId(),
                previous.version(),
                replacement,
                expirationPolicy.instant()) == SessionUpdateOutcome.UPDATED;
    }

    private ActiveSessionSnapshot find(SessionId sessionId) {
        return sessionStore.find(sessionId, expirationPolicy.instant()).orElse(null);
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

    /**
     * Solo commands must name the current version. Multiplayer answers and skip votes only need
     * to come from the current round, since other players' commands advance the version.
     */
    private boolean isFresh(ActiveSessionSnapshot session, SessionCommand command) {
        long expected = command.expectedSessionVersion();
        if (expected == session.version()) {
            return true;
        }
        boolean roundCommand = command.type() == SessionCommandType.SUBMIT_ANSWER
                || command.type() == SessionCommandType.SKIP_ROUND;
        return session.isMultiplayer() && roundCommand
                && expected >= session.roundStartedVersion() && expected < session.version();
    }

    private List<PublicSessionEventPayload.ParticipantScore> participantScores(
            ActiveSessionSnapshot session) {
        GameStateSnapshot game = session.gameState();
        return session.participants().stream().
                map(member -> new PublicSessionEventPayload.ParticipantScore(
                        member.participantId().value(),
                        member.displayName(),
                        game.currentRound().
                                map(round -> round.scoreState().pointsFor(member.participantId())).
                                orElse(0),
                        game.totalScoreFor(member.participantId()))).
                toList();
    }

    private String displayName(ActiveSessionSnapshot session, GameParticipantId participantId) {
        return session.participants().stream().
                filter(member -> member.participantId().equals(participantId)).
                map(SessionParticipant::displayName).
                findFirst().
                orElseThrow();
    }

    /** Every participant counts as connected until presence tracking exists (MP-07). */
    private Set<GameParticipantId> activeParticipants(ActiveSessionSnapshot session) {
        return Set.copyOf(session.gameState().participants());
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
