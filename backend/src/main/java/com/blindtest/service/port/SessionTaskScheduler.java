package com.blindtest.service.port;

import com.blindtest.model.session.SessionId;
import java.time.Instant;

/** Injectable boundary for authoritative active-session deadlines. */
@FunctionalInterface
public interface SessionTaskScheduler {

    /** Schedules one idempotent task for the requested authoritative instant. */
    void schedule(Instant executeAt, Runnable task);

    /** Schedules cancellable volatile gameplay work owned by one active session. */
    default void schedule(SessionId sessionId, Instant executeAt, Runnable task) {
        schedule(executeAt, task);
    }

    /** Cancels queued gameplay work for an expired session and returns the task count. */
    default int cancelSessionTasks(SessionId sessionId) {
        return 0;
    }
}
