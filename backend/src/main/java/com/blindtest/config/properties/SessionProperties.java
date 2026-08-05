package com.blindtest.config.properties;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/** Bounded retention and maintenance settings for process-local gameplay sessions. */
@Validated
@ConfigurationProperties("app.session")
public record SessionProperties(
        @DefaultValue("30m") @NotNull Duration inactiveTtl,
        @DefaultValue("5m") @NotNull Duration finishedTtl,
        @DefaultValue("PT1M") @NotNull Duration cleanupDelay) {

    private static final Duration MAXIMUM_INACTIVE_TTL = Duration.ofHours(24);
    private static final Duration MAXIMUM_FINISHED_TTL = Duration.ofHours(1);
    private static final Duration MAXIMUM_CLEANUP_DELAY = Duration.ofMinutes(10);

    /** Rejects settings that could retain active or sensitive process state indefinitely. */
    public SessionProperties {
        requireBounded(inactiveTtl, MAXIMUM_INACTIVE_TTL, "inactive TTL");
        requireBounded(finishedTtl, MAXIMUM_FINISHED_TTL, "finished TTL");
        requireBounded(cleanupDelay, MAXIMUM_CLEANUP_DELAY, "cleanup delay");
    }

    private static void requireBounded(Duration value, Duration maximum, String name) {
        if (value != null && (value.isZero() || value.isNegative()
                || value.compareTo(maximum) > 0)) {
            throw new IllegalArgumentException(
                    "Session " + name + " must be positive and no longer than " + maximum);
        }
    }
}
