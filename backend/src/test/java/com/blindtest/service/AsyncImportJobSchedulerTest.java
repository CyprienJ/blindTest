package com.blindtest.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.UUID;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class AsyncImportJobSchedulerTest {

    private final ImportJobRunner runner = mock(ImportJobRunner.class);
    private final Executor executor = mock(Executor.class);
    private final AsyncImportJobScheduler scheduler = new AsyncImportJobScheduler(runner, executor);

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void schedulesImmediatelyWhenThereIsNoTransaction() {
        UUID jobId = UUID.randomUUID();

        scheduler.schedule(jobId);

        Runnable task = captureTask();
        verifyNoInteractions(runner);
        task.run();
        verify(runner).execute(jobId);
    }

    @Test
    void schedulesOnlyAfterTheCreatingTransactionCommits() {
        UUID jobId = UUID.randomUUID();
        TransactionSynchronizationManager.initSynchronization();

        scheduler.schedule(jobId);

        verifyNoInteractions(executor, runner);
        var synchronizations = TransactionSynchronizationManager.getSynchronizations();
        assertThat(synchronizations).hasSize(1);
        synchronizations.getFirst().afterCommit();

        Runnable task = captureTask();
        verifyNoInteractions(runner);
        task.run();
        verify(runner).execute(jobId);
    }

    private Runnable captureTask() {
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(executor).execute(task.capture());
        return task.getValue();
    }
}
