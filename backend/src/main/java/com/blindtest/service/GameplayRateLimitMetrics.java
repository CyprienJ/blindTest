package com.blindtest.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/** Safe aggregate counters for gameplay commands rejected before domain processing. */
@Component
public class GameplayRateLimitMetrics {

    private final Counter playerCooldownRejected;
    private final Counter sessionCommandRejected;

    /** Registers counters without session, participant, account, or answer labels. */
    public GameplayRateLimitMetrics(MeterRegistry registry) {
        playerCooldownRejected = registry.counter(
                "blindtest.gameplay.rate.limit.rejected", "scope", "player_cooldown");
        sessionCommandRejected = registry.counter(
                "blindtest.gameplay.rate.limit.rejected", "scope", "session_command");
    }

    /** Records one answer rejected by the short per-player cooldown. */
    public void recordPlayerCooldownRejected() {
        playerCooldownRejected.increment();
    }

    /** Records one command rejected by the per-session burst guard. */
    public void recordSessionCommandRejected() {
        sessionCommandRejected.increment();
    }
}
