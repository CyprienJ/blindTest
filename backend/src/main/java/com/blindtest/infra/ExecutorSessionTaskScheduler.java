package com.blindtest.infra;

import com.blindtest.model.session.SessionId;
import com.blindtest.service.port.SessionTaskScheduler;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/** Dedicated process-local scheduler that does not replace Spring's maintenance scheduler. */
@Component
public class ExecutorSessionTaskScheduler implements SessionTaskScheduler {

    private final Clock clock;
    private final AtomicLong taskSequence = new AtomicLong();
    private final ConcurrentMap<SessionId, ConcurrentMap<Long, ScheduledFuture<?>>> sessionTasks =
            new ConcurrentHashMap<>();
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
        scheduleInternal(null, executeAt, task);
    }

    @Override
    public void schedule(SessionId sessionId, Instant executeAt, Runnable task) {
        Objects.requireNonNull(sessionId, "sessionId is required");
        scheduleInternal(sessionId, executeAt, task);
    }

    @Override
    public int cancelSessionTasks(SessionId sessionId) {
        Objects.requireNonNull(sessionId, "sessionId is required");
        ConcurrentMap<Long, ScheduledFuture<?>> removed = sessionTasks.remove(sessionId);
        if (removed == null) {
            return 0;
        }
        removed.values().forEach(future -> future.cancel(false));
        return removed.size();
    }

    private void scheduleInternal(SessionId sessionId, Instant executeAt, Runnable task) {
        Objects.requireNonNull(executeAt, "executeAt is required");
        Objects.requireNonNull(task, "task is required");
        Duration remaining = Duration.between(clock.instant(), executeAt);
        long delayNanos = remaining.isNegative() ? 0 : remaining.toNanos();
        if (sessionId == null) {
            executor.schedule(task, delayNanos, TimeUnit.NANOSECONDS);
            return;
        }
        long taskId = taskSequence.incrementAndGet();
        Runnable wrapped = () -> {
            try {
                task.run();
            } finally {
                forgetCompleted(sessionId, taskId);
            }
        };
        ScheduledFuture<?> future = executor.schedule(wrapped, delayNanos, TimeUnit.NANOSECONDS);
        sessionTasks.computeIfAbsent(sessionId, ignored -> new ConcurrentHashMap<>()).
                put(taskId, future);
        if (future.isDone()) {
            forgetCompleted(sessionId, taskId);
        }
    }

    private void forgetCompleted(SessionId sessionId, long taskId) {
        sessionTasks.computeIfPresent(sessionId, (ignored, tasks) -> {
            tasks.remove(taskId);
            return tasks.isEmpty() ? null : tasks;
        });
    }

    /** Stops daemon scheduling threads during application shutdown. */
    @PreDestroy
    public void shutdown() {
        sessionTasks.clear();
        executor.shutdownNow();
    }
}
