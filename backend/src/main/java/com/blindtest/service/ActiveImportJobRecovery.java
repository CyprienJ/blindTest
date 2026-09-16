package com.blindtest.service;

import com.blindtest.model.entity.ImportJobStatus;
import com.blindtest.repository.TrackImportJobRepository;
import com.blindtest.service.port.ImportJobScheduler;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Restarts persisted import work that was interrupted before reaching a terminal state. */
@Component
@RequiredArgsConstructor
@Slf4j
public class ActiveImportJobRecovery {

    private static final EnumSet<ImportJobStatus> ACTIVE_STATUSES = EnumSet.of(
            ImportJobStatus.QUEUED, ImportJobStatus.DOWNLOADING, ImportJobStatus.PROCESSING);

    private final TrackImportJobRepository jobRepository;
    private final ImportJobScheduler importJobScheduler;

    /** Reschedules durable active jobs once the application is ready to serve requests. */
    @EventListener(ApplicationReadyEvent.class)
    public void recoverActiveJobs() {
        List<UUID> activeJobIds = jobRepository.findIdsByStatusIn(ACTIVE_STATUSES);
        if (activeJobIds.isEmpty()) {
            return;
        }
        log.info("Rescheduling {} persisted active import job(s)", activeJobIds.size());
        activeJobIds.forEach(importJobScheduler::schedule);
    }
}
