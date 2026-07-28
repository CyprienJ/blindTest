package com.blindtest.service.port;

import java.util.UUID;

/** Port that schedules a persisted import job for execution. */
@FunctionalInterface
public interface ImportJobScheduler {

    /** Schedules the job identified by {@code jobId}. */
    void schedule(UUID jobId);
}
