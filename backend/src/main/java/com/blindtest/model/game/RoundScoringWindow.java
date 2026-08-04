package com.blindtest.model.game;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** Server-authoritative deadline and configured duration used for title scoring. */
public record RoundScoringWindow(Instant deadline, Duration configuredDuration) {

    /** Rejects missing deadlines and non-positive configured durations. */
    public RoundScoringWindow {
        Objects.requireNonNull(deadline, "deadline is required");
        Objects.requireNonNull(configuredDuration, "configuredDuration is required");
        if (configuredDuration.isZero() || configuredDuration.isNegative()) {
            throw new IllegalArgumentException("configuredDuration must be positive");
        }
    }

    /** Returns the nominal round start derived from the authoritative deadline. */
    public Instant startsAt() {
        return deadline.minus(configuredDuration);
    }
}
