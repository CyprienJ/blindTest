package com.blindtest.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

/** Low-cardinality process metrics for active-session expiry and resource release. */
@Component
public class SessionCleanupMetrics {

    private final Counter successfulRuns;
    private final Counter failedRuns;
    private final Counter abandoned;
    private final Counter terminal;
    private final Counter cancelledTimers;
    private final AtomicInteger activeSessions = new AtomicInteger();

    /** Registers aggregate metrics without session, account, track, or answer labels. */
    public SessionCleanupMetrics(MeterRegistry registry) {
        successfulRuns = registry.counter("blindtest.session.cleanup.runs", "outcome", "success");
        failedRuns = registry.counter("blindtest.session.cleanup.runs", "outcome", "failure");
        abandoned = registry.counter("blindtest.session.cleanup.removed", "outcome", "abandoned");
        terminal = registry.counter("blindtest.session.cleanup.removed", "outcome", "terminal");
        cancelledTimers = registry.counter("blindtest.session.cleanup.timers.cancelled");
        Gauge.builder("blindtest.session.active", activeSessions, AtomicInteger::get).
                description("Unexpired process-local gameplay sessions").
                register(registry);
    }

    public void recordSuccess(int activeCount) {
        activeSessions.set(activeCount);
        successfulRuns.increment();
    }

    public void recordFailure() {
        failedRuns.increment();
    }

    public void recordAbandoned() {
        abandoned.increment();
    }

    public void recordTerminal() {
        terminal.increment();
    }

    public void recordCancelledTimers(int count) {
        cancelledTimers.increment(count);
    }
}
