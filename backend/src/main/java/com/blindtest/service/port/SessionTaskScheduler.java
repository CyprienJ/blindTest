package com.blindtest.service.port;

import java.time.Instant;

/** Injectable boundary for authoritative active-session deadlines. */
@FunctionalInterface
public interface SessionTaskScheduler {

    /** Schedules one idempotent task for the requested authoritative instant. */
    void schedule(Instant executeAt, Runnable task);
}
