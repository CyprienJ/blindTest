package com.blindtest.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.blindtest.model.game.GameParticipantId;
import com.blindtest.model.session.SessionId;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GameplayCommandRateLimiterTest {

    private static final Instant NOW = Instant.parse("2026-08-04T15:00:00Z");
    private static final SessionId SESSION_ID = new SessionId(UUID.randomUUID());
    private static final GameParticipantId PARTICIPANT_ID =
            new GameParticipantId(UUID.randomUUID());

    private GameplayRateLimitMetrics metrics;
    private GameplayCommandRateLimiter limiter;

    @BeforeEach
    void setUp() {
        metrics = mock(GameplayRateLimitMetrics.class);
        limiter = new GameplayCommandRateLimiter(metrics);
    }

    @Test
    void answerCooldownAllowsNormalTypingAndSuppressesRepeatedFeedback() {
        assertThat(limiter.checkAnswer(SESSION_ID, PARTICIPANT_ID, NOW).action()).
                isEqualTo(GameplayCommandRateLimiter.RateLimitAction.ALLOW);

        var firstRejection = limiter.checkAnswer(
                SESSION_ID, PARTICIPANT_ID, NOW.plusMillis(100));
        var suppressed = limiter.checkAnswer(
                SESSION_ID, PARTICIPANT_ID, NOW.plusMillis(200));
        var nextAccepted = limiter.checkAnswer(
                SESSION_ID, PARTICIPANT_ID, NOW.plusMillis(300));

        assertThat(firstRejection.action()).isEqualTo(
                GameplayCommandRateLimiter.RateLimitAction.REJECT_WITH_COOLDOWN);
        assertThat(firstRejection.retryAt()).contains(NOW.plusMillis(300));
        assertThat(suppressed.action()).isEqualTo(
                GameplayCommandRateLimiter.RateLimitAction.DROP_SILENTLY);
        assertThat(nextAccepted.action()).isEqualTo(
                GameplayCommandRateLimiter.RateLimitAction.ALLOW);
        verify(metrics, times(2)).recordPlayerCooldownRejected();
    }

    @Test
    void sessionBurstHasOneFeedbackThenResetsAtTheWindowBoundary() {
        for (int index = 0;
                index < GameplayCommandRateLimiter.MAXIMUM_COMMANDS_PER_SESSION_WINDOW;
                index++) {
            assertThat(limiter.checkSession(SESSION_ID, NOW).action()).
                    isEqualTo(GameplayCommandRateLimiter.RateLimitAction.ALLOW);
        }

        assertThat(limiter.checkSession(SESSION_ID, NOW).action()).isEqualTo(
                GameplayCommandRateLimiter.RateLimitAction.REJECT_SESSION_WITH_FEEDBACK);
        assertThat(limiter.checkSession(SESSION_ID, NOW).action()).isEqualTo(
                GameplayCommandRateLimiter.RateLimitAction.DROP_SILENTLY);
        assertThat(limiter.checkSession(
                SESSION_ID,
                NOW.plus(GameplayCommandRateLimiter.SESSION_COMMAND_WINDOW)).action()).
                isEqualTo(GameplayCommandRateLimiter.RateLimitAction.ALLOW);
        verify(metrics, times(2)).recordSessionCommandRejected();
    }

    @Test
    void sessionsAreIsolatedAndForgottenStateStartsFresh() {
        SessionId otherSession = new SessionId(UUID.randomUUID());
        for (int index = 0;
                index < GameplayCommandRateLimiter.MAXIMUM_COMMANDS_PER_SESSION_WINDOW;
                index++) {
            limiter.checkSession(SESSION_ID, NOW);
        }

        assertThat(limiter.checkSession(otherSession, NOW).action()).
                isEqualTo(GameplayCommandRateLimiter.RateLimitAction.ALLOW);
        limiter.forget(SESSION_ID);
        assertThat(limiter.checkSession(SESSION_ID, NOW).action()).
                isEqualTo(GameplayCommandRateLimiter.RateLimitAction.ALLOW);
    }
}
