package com.blindtest.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.blindtest.config.properties.SessionProperties;
import com.blindtest.model.game.GameStatus;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class SessionExpirationPolicyTest {

    private static final Instant NOW = Instant.parse("2026-08-04T17:00:00Z");

    @Test
    void appliesSlidingInactiveAndShortTerminalTtls() {
        SessionExpirationPolicy policy = new SessionExpirationPolicy(
                properties(), java.time.Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(policy.expiresAt(GameStatus.CREATED, NOW)).
                isEqualTo(NOW.plus(Duration.ofMinutes(30)));
        assertThat(policy.expiresAt(GameStatus.PLAYING, NOW)).
                isEqualTo(NOW.plus(Duration.ofMinutes(30)));
        assertThat(policy.expiresAt(GameStatus.FINISHED, NOW)).
                isEqualTo(NOW.plus(Duration.ofMinutes(5)));
        assertThat(policy.expiresAt(GameStatus.ABORTED, NOW)).
                isEqualTo(NOW.plus(Duration.ofMinutes(5)));
    }

    @Test
    void rejectsUnboundedOrNonPositiveCleanupSettings() {
        assertThatThrownBy(() -> new SessionProperties(
                        Duration.ofDays(2), Duration.ofMinutes(5), Duration.ofMinutes(1))).
                isInstanceOf(IllegalArgumentException.class).
                hasMessageContaining("inactive TTL");
        assertThatThrownBy(() -> new SessionProperties(
                        Duration.ofMinutes(30), Duration.ZERO, Duration.ofMinutes(1))).
                isInstanceOf(IllegalArgumentException.class).
                hasMessageContaining("finished TTL");
        assertThatThrownBy(() -> new SessionProperties(
                        Duration.ofMinutes(30), Duration.ofMinutes(5), Duration.ofHours(1))).
                isInstanceOf(IllegalArgumentException.class).
                hasMessageContaining("cleanup delay");
    }

    private SessionProperties properties() {
        return new SessionProperties(
                Duration.ofMinutes(30), Duration.ofMinutes(5), Duration.ofMinutes(1));
    }
}
