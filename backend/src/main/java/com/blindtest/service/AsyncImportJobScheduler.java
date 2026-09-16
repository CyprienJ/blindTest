package com.blindtest.service;

import com.blindtest.config.AsyncConfig;
import com.blindtest.service.port.ImportJobScheduler;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Schedules persisted import work only after the creating transaction commits. */
@Component
public class AsyncImportJobScheduler implements ImportJobScheduler {

    private final ImportJobRunner importJobRunner;
    private final Executor importTaskExecutor;

    public AsyncImportJobScheduler(
            ImportJobRunner importJobRunner,
            @Qualifier(AsyncConfig.IMPORT_EXECUTOR) Executor importTaskExecutor) {
        this.importJobRunner = importJobRunner;
        this.importTaskExecutor = importTaskExecutor;
    }

    @Override
    public void schedule(UUID jobId) {
        Runnable task = () -> importJobRunner.execute(jobId);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            importTaskExecutor.execute(task);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        importTaskExecutor.execute(task);
                    }
                });
    }
}
