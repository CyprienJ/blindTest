package com.blindtest.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/** Aggregate completion counters without session, participant, or track labels. */
@Component
public class GameHistoryCompletionMetrics {

    private final Counter created;
    private final Counter alreadyExists;
    private final Counter retryScheduled;
    private final Counter failed;

    /** Registers low-cardinality durable-completion outcome counters. */
    public GameHistoryCompletionMetrics(MeterRegistry registry) {
        created = registry.counter("blindtest.game.history.completion", "outcome", "created");
        alreadyExists = registry.counter(
                "blindtest.game.history.completion", "outcome", "already_exists");
        retryScheduled = registry.counter(
                "blindtest.game.history.completion", "outcome", "retry_scheduled");
        failed = registry.counter("blindtest.game.history.completion", "outcome", "failed");
    }

    public void recordCreated() {
        created.increment();
    }

    public void recordAlreadyExists() {
        alreadyExists.increment();
    }

    public void recordRetryScheduled() {
        retryScheduled.increment();
    }

    public void recordFailed() {
        failed.increment();
    }
}
