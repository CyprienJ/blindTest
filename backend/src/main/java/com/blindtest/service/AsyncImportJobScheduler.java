package com.blindtest.service;

import com.blindtest.service.port.ImportJobScheduler;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/** Production scheduler that delegates import work to the configured async executor. */
@Component
@RequiredArgsConstructor
public class AsyncImportJobScheduler implements ImportJobScheduler {

    private final ImportJobRunner importJobRunner;

    @Override
    @Async
    public void schedule(UUID jobId) {
        importJobRunner.execute(jobId);
    }
}
