package com.blindtest.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class GameplayRateLimitMetricsTest {

    @Test
    void recordsOnlySafeAggregateScopeCounters() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        GameplayRateLimitMetrics metrics = new GameplayRateLimitMetrics(registry);

        metrics.recordPlayerCooldownRejected();
        metrics.recordSessionCommandRejected();
        metrics.recordSessionCommandRejected();

        assertThat(registry.counter(
                "blindtest.gameplay.rate.limit.rejected",
                "scope",
                "player_cooldown").count()).isEqualTo(1);
        assertThat(registry.counter(
                "blindtest.gameplay.rate.limit.rejected",
                "scope",
                "session_command").count()).isEqualTo(2);
        assertThat(registry.getMeters()).allSatisfy(meter ->
                assertThat(meter.getId().getTags()).allSatisfy(tag ->
                        assertThat(tag.getKey()).isEqualTo("scope")));
    }
}
