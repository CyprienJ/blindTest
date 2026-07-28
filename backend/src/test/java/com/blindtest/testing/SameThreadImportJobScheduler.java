package com.blindtest.testing;

import com.blindtest.service.ImportJobRunner;
import com.blindtest.service.port.ImportJobScheduler;
import java.util.UUID;

/** Deterministic scheduler that completes import work on the calling test thread. */
public class SameThreadImportJobScheduler implements ImportJobScheduler {

    private final ImportJobRunner importJobRunner;

    public SameThreadImportJobScheduler(ImportJobRunner importJobRunner) {
        this.importJobRunner = importJobRunner;
    }

    @Override
    public void schedule(UUID jobId) {
        importJobRunner.execute(jobId);
    }
}
