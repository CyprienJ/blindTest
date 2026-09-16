package com.blindtest.service;

import com.blindtest.model.entity.MediaDeletionTaskEntity;
import com.blindtest.repository.MediaDeletionTaskRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Polls due media-deletion tasks in bounded batches. */
@Component
@RequiredArgsConstructor
public class MediaDeletionWorker {

    private final MediaDeletionTaskRepository deletionTaskRepository;
    private final MediaDeletionTaskProcessor processor;
    private final Clock clock;

    /** Processes the next due deletion batch. */
    @Scheduled(fixedDelayString = "${app.media-maintenance.deletion-poll-delay-ms:30000}")
    public void processDueTasks() {
        List<MediaDeletionTaskEntity> dueTasks = deletionTaskRepository.
                findTop25ByNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
                        LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        dueTasks.forEach(task -> processor.process(task.getId()));
    }
}
