package com.blindtest.service;

import com.blindtest.model.entity.GameHistoryEntity;
import com.blindtest.model.entity.GameHistoryParticipantEntity;
import com.blindtest.model.entity.GameHistoryRoundEntity;
import com.blindtest.model.entity.HistoryParticipantKind;
import com.blindtest.model.game.GameAnswerSnapshot;
import com.blindtest.model.game.GameStatus;
import com.blindtest.model.game.GameSummary;
import com.blindtest.model.game.RoundStateSnapshot;
import com.blindtest.model.session.ActiveSessionSnapshot;
import com.blindtest.model.session.SessionParticipant;
import com.blindtest.repository.GameHistoryRepository;
import com.blindtest.repository.UserRepository;
import com.blindtest.websocket.protocol.SessionProtocol;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Atomically converts a finished in-memory game into its durable, immutable history snapshot. */
@Service
public class GameHistoryPersistenceService {

    static final String HISTORY_VERSION = "history-v1";
    static final String GAME_MODE = "SOLO";
    static final String CONFIGURATION_VERSION = "solo-v1";
    static final String ANSWER_SNAPSHOT_VERSION = "answers-v1";
    static final int RETENTION_MONTHS = 24;

    private final GameHistoryRepository historyRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    /** Wires durable history, optional account association, and stable JSON serialization. */
    public GameHistoryPersistenceService(
            GameHistoryRepository historyRepository,
            UserRepository userRepository,
            ObjectMapper objectMapper) {
        this.historyRepository = historyRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    /** Persists the full history aggregate once, in one transaction keyed by session identity. */
    @Transactional
    public GameHistoryPersistenceOutcome persist(ActiveSessionSnapshot session) {
        requireFinished(session);
        UUID sessionId = session.sessionId().value();
        if (historyRepository.existsBySessionId(sessionId)) {
            return GameHistoryPersistenceOutcome.ALREADY_EXISTS;
        }
        historyRepository.saveAndFlush(toHistory(session));
        return GameHistoryPersistenceOutcome.CREATED;
    }

    /** Confirms durable success in a fresh repository transaction after a concurrent insert race. */
    public boolean exists(UUID sessionId) {
        return historyRepository.existsBySessionId(sessionId);
    }

    private GameHistoryEntity toHistory(ActiveSessionSnapshot session) {
        GameSummary summary = session.gameState().finalSummary().orElseThrow();
        RoundStateSnapshot firstRound = session.gameState().completedRounds().getFirst();
        RoundStateSnapshot lastRound = session.gameState().completedRounds().getLast();
        var completedAt = lastRound.completedAt().orElseThrow();

        GameHistoryEntity history = new GameHistoryEntity();
        history.setSessionId(session.sessionId().value());
        history.setHistoryVersion(HISTORY_VERSION);
        history.setGameMode(GAME_MODE);
        history.setProtocolVersion((short) SessionProtocol.VERSION);
        history.setConfigurationVersion(CONFIGURATION_VERSION);
        history.setPlaylistIdSnapshot(session.configuration().playlistId());
        history.setRequestedTrackCount(session.configuration().requestedTrackCount());
        history.setMaximumRoundDurationSeconds(
                session.configuration().maximumRoundDurationSeconds());
        history.setConfigurationExtensions(toJson(session.configuration().extensions()));
        history.setTotalScore(summary.totalScore());
        history.setTotalRounds(summary.totalRounds());
        history.setTitlesFound(summary.titlesFound());
        history.setSkippedRounds(summary.skippedRounds());
        history.setTimedOutRounds(summary.timedOutRounds());
        history.setStartedAt(firstRound.scoringWindow().orElseThrow().startsAt());
        history.setCompletedAt(completedAt);
        history.setRetentionExpiresAt(completedAt.atZone(ZoneOffset.UTC).
                plusMonths(RETENTION_MONTHS).toInstant());
        history.addParticipant(toParticipant(session, summary));
        session.gameState().completedRounds().stream().
                map(this::toRound).
                forEach(history::addRound);
        return history;
    }

    private GameHistoryParticipantEntity toParticipant(
            ActiveSessionSnapshot session,
            GameSummary summary) {
        SessionParticipant owner = session.participants().stream().
                filter(participant -> participant.participantId().equals(
                        session.ownerParticipantId())).
                findFirst().
                orElseThrow();
        GameHistoryParticipantEntity participant = new GameHistoryParticipantEntity();
        participant.setParticipantId(owner.participantId().value());
        owner.accountId().flatMap(userRepository::findById).ifPresent(participant::setUser);
        participant.setDisplayName(owner.displayName());
        participant.setParticipantKind(owner.accountId().isPresent()
                ? HistoryParticipantKind.AUTHENTICATED : HistoryParticipantKind.GUEST);
        participant.setOwner(true);
        participant.setFinalScore(summary.totalScore());
        participant.setFinalRank(1);
        return participant;
    }

    private GameHistoryRoundEntity toRound(RoundStateSnapshot snapshot) {
        GameAnswerSnapshot answers = snapshot.track().answers();
        var timing = snapshot.scoringWindow().orElseThrow();
        GameHistoryRoundEntity round = new GameHistoryRoundEntity();
        round.setRoundIndex(snapshot.track().playOrder());
        round.setTrackIdSnapshot(snapshot.track().trackId());
        round.setTitleSnapshot(answers.title());
        round.setArtistSnapshot(answers.artist());
        round.setComposerSnapshot(answers.composer());
        round.setAlbumSnapshot(answers.album());
        round.setAnswerAliasesSnapshot(toJson(answers.aliases()));
        round.setAnswerSnapshotVersion(ANSWER_SNAPSHOT_VERSION);
        round.setExcerptStartSeconds(snapshot.track().playback().excerptStartSeconds());
        round.setOutcome(snapshot.outcome().orElseThrow());
        round.setPoints(snapshot.scoreState().totalPoints());
        round.setScoringRuleVersion(snapshot.scoreState().scoringRuleVersion());
        round.setStartedAt(timing.startsAt());
        round.setDeadline(timing.deadline());
        round.setRevealedAt(snapshot.revealedAt().orElseThrow());
        round.setCompletedAt(snapshot.completedAt().orElseThrow());
        return round;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize durable history snapshot", exception);
        }
    }

    private static void requireFinished(ActiveSessionSnapshot session) {
        if (session == null || session.gameState().status() != GameStatus.FINISHED
                || session.gameState().finalSummary().isEmpty()) {
            throw new IllegalArgumentException("only a finished session can be persisted");
        }
    }
}
