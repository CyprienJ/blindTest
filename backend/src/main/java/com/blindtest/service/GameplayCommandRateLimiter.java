package com.blindtest.service;

import com.blindtest.model.game.GameParticipantId;
import com.blindtest.model.session.SessionId;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** Process-local burst and answer-cooldown guard evaluated before expensive gameplay work. */
@Component
public class GameplayCommandRateLimiter {

    static final Duration PLAYER_ANSWER_COOLDOWN = Duration.ofMillis(300);
    static final Duration SESSION_COMMAND_WINDOW = Duration.ofSeconds(1);
    static final int MAXIMUM_COMMANDS_PER_SESSION_WINDOW = 20;

    private final Map<SessionId, SessionRateState> sessions = new ConcurrentHashMap<>();
    private final GameplayRateLimitMetrics metrics;

    public GameplayCommandRateLimiter(GameplayRateLimitMetrics metrics) {
        this.metrics = metrics;
    }

    /** Applies the bounded one-player session command budget. */
    public RateLimitDecision checkSession(SessionId sessionId, Instant now) {
        return checkSession(sessionId, now, 1);
    }

    /**
     * Applies the per-session command budget, scaled by participant count so one player's
     * share stays constant, before any command-specific work.
     */
    public RateLimitDecision checkSession(SessionId sessionId, Instant now, int participants) {
        Objects.requireNonNull(sessionId, "sessionId is required");
        Objects.requireNonNull(now, "now is required");
        if (participants < 1) {
            throw new IllegalArgumentException("participants must be positive");
        }
        return sessions.computeIfAbsent(sessionId, ignored -> new SessionRateState()).
                checkSession(now, MAXIMUM_COMMANDS_PER_SESSION_WINDOW * participants, metrics);
    }

    /** Applies the short cooldown before answer normalization and matching. */
    public RateLimitDecision checkAnswer(
            SessionId sessionId,
            GameParticipantId participantId,
            Instant now) {
        Objects.requireNonNull(sessionId, "sessionId is required");
        Objects.requireNonNull(participantId, "participantId is required");
        Objects.requireNonNull(now, "now is required");
        return sessions.computeIfAbsent(sessionId, ignored -> new SessionRateState()).
                checkAnswer(participantId, now, metrics);
    }

    /** Drops volatile guard state when the canonical active session is unavailable. */
    public void forget(SessionId sessionId) {
        sessions.remove(Objects.requireNonNull(sessionId, "sessionId is required"));
    }

    /** Result determining whether one bounded feedback event may be emitted. */
    public record RateLimitDecision(
            RateLimitAction action,
            Optional<Instant> retryAt) {

        /** Ensures only a cooldown rejection carries a retry deadline. */
        public RateLimitDecision {
            Objects.requireNonNull(action, "action is required");
            Objects.requireNonNull(retryAt, "retryAt is required");
            if ((action == RateLimitAction.REJECT_WITH_COOLDOWN) != retryAt.isPresent()) {
                throw new IllegalArgumentException("retryAt is required only for cooldown");
            }
        }

        static RateLimitDecision allow() {
            return new RateLimitDecision(RateLimitAction.ALLOW, Optional.empty());
        }

        static RateLimitDecision rejectSession() {
            return new RateLimitDecision(
                    RateLimitAction.REJECT_SESSION_WITH_FEEDBACK, Optional.empty());
        }

        static RateLimitDecision rejectCooldown(Instant retryAt) {
            return new RateLimitDecision(
                    RateLimitAction.REJECT_WITH_COOLDOWN, Optional.of(retryAt));
        }

        static RateLimitDecision drop() {
            return new RateLimitDecision(RateLimitAction.DROP_SILENTLY, Optional.empty());
        }
    }

    /** Bounded outcomes keep abusive traffic from producing unbounded feedback. */
    public enum RateLimitAction {
        ALLOW,
        REJECT_WITH_COOLDOWN,
        REJECT_SESSION_WITH_FEEDBACK,
        DROP_SILENTLY
    }

    private static final class SessionRateState {

        private final Map<GameParticipantId, PlayerRateState> players = new HashMap<>();
        private Instant commandWindowStartedAt;
        private int commandsInWindow;
        private boolean sessionFeedbackSent;

        synchronized RateLimitDecision checkSession(
                Instant now,
                int maximumCommands,
                GameplayRateLimitMetrics metrics) {
            if (commandWindowStartedAt == null
                    || now.isBefore(commandWindowStartedAt)
                    || !now.isBefore(commandWindowStartedAt.plus(SESSION_COMMAND_WINDOW))) {
                commandWindowStartedAt = now;
                commandsInWindow = 0;
                sessionFeedbackSent = false;
            }
            commandsInWindow++;
            if (commandsInWindow <= maximumCommands) {
                return RateLimitDecision.allow();
            }
            metrics.recordSessionCommandRejected();
            if (!sessionFeedbackSent) {
                sessionFeedbackSent = true;
                return RateLimitDecision.rejectSession();
            }
            return RateLimitDecision.drop();
        }

        synchronized RateLimitDecision checkAnswer(
                GameParticipantId participantId,
                Instant now,
                GameplayRateLimitMetrics metrics) {
            PlayerRateState player = players.computeIfAbsent(
                    participantId, ignored -> new PlayerRateState());
            if (player.lastAcceptedAt == null
                    || now.isBefore(player.lastAcceptedAt)
                    || !now.isBefore(player.lastAcceptedAt.plus(PLAYER_ANSWER_COOLDOWN))) {
                player.lastAcceptedAt = now;
                player.cooldownFeedbackSent = false;
                return RateLimitDecision.allow();
            }
            metrics.recordPlayerCooldownRejected();
            if (!player.cooldownFeedbackSent) {
                player.cooldownFeedbackSent = true;
                return RateLimitDecision.rejectCooldown(
                        player.lastAcceptedAt.plus(PLAYER_ANSWER_COOLDOWN));
            }
            return RateLimitDecision.drop();
        }
    }

    private static final class PlayerRateState {

        private Instant lastAcceptedAt;
        private boolean cooldownFeedbackSent;
    }
}
