package com.blindtest.infra;

import com.blindtest.service.port.SessionTaskScheduler;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/** Dedicated process-local scheduler that does not replace Spring's maintenance scheduler. */
@Component
public class ExecutorSessionTaskScheduler implements SessionTaskScheduler {

    private final Clock clock;
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(
            2,
            Thread.ofPlatform().
                    daemon().
                    name("session-deadline-", 0).
                    factory());

    public ExecutorSessionTaskScheduler(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock is required");
    }

    @Override
    public void schedule(Instant executeAt, Runnable task) {
        Objects.requireNonNull(executeAt, "executeAt is required");
        Objects.requireNonNull(task, "task is required");
        Duration remaining = Duration.between(clock.instant(), executeAt);
        long delayNanos = remaining.isNegative() ? 0 : remaining.toNanos();
        executor.schedule(task, delayNanos, TimeUnit.NANOSECONDS);
    }

    /** Stops daemon scheduling threads during application shutdown. */
    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }
}
