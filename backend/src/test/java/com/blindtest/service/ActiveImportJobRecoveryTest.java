package com.blindtest.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.blindtest.model.entity.ImportJobStatus;
import com.blindtest.repository.TrackImportJobRepository;
import com.blindtest.service.port.ImportJobScheduler;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ActiveImportJobRecoveryTest {

    private static final EnumSet<ImportJobStatus> ACTIVE_STATUSES = EnumSet.of(
            ImportJobStatus.QUEUED, ImportJobStatus.DOWNLOADING, ImportJobStatus.PROCESSING);

    private final TrackImportJobRepository jobRepository = mock(TrackImportJobRepository.class);
    private final ImportJobScheduler scheduler = mock(ImportJobScheduler.class);
    private final ActiveImportJobRecovery recovery =
            new ActiveImportJobRecovery(jobRepository, scheduler);

    @Test
    void reschedulesEveryPersistedActiveJob() {
        UUID queuedJob = UUID.randomUUID();
        UUID interruptedJob = UUID.randomUUID();
        when(jobRepository.findIdsByStatusIn(ACTIVE_STATUSES)).
                thenReturn(List.of(queuedJob, interruptedJob));

        recovery.recoverActiveJobs();

        verify(scheduler).schedule(queuedJob);
        verify(scheduler).schedule(interruptedJob);
    }

    @Test
    void doesNothingWhenThereAreNoActiveJobs() {
        when(jobRepository.findIdsByStatusIn(ACTIVE_STATUSES)).thenReturn(List.of());

        recovery.recoverActiveJobs();

        verify(scheduler, never()).schedule(org.mockito.ArgumentMatchers.any());
    }
}
