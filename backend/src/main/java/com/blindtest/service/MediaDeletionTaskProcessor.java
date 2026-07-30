package com.blindtest.service;

import com.blindtest.model.entity.MediaDeletionTaskEntity;
import com.blindtest.repository.MediaDeletionTaskRepository;
import com.blindtest.service.port.MediaStorage;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Processes one durable media-deletion request and records retry state. */
@Service
@RequiredArgsConstructor
@Slf4j
public class MediaDeletionTaskProcessor {

    private static final Duration INITIAL_BACKOFF = Duration.ofSeconds(30);
    private static final Duration MAX_BACKOFF = Duration.ofHours(24);

    private final MediaDeletionTaskRepository deletionTaskRepository;
    private final MediaStorage mediaStorage;
    private final MediaOperationalMetrics metrics;
    private final Clock clock;

    /** Attempts one idempotent object deletion. */
    @Transactional
    public void process(UUID taskId) {
        deletionTaskRepository.findById(taskId).ifPresent(this::attemptDeletion);
    }

    private void attemptDeletion(MediaDeletionTaskEntity task) {
        try {
            mediaStorage.delete(task.getObjectKey());
            deletionTaskRepository.delete(task);
            metrics.recordDeletionSucceeded();
        } catch (RuntimeException exception) {
            int attempts = task.getAttemptCount() + 1;
            task.setAttemptCount(attempts);
            task.setNextAttemptAt(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).
                    plus(backoff(attempts)));
            task.setLastError(exception.getClass().getSimpleName());
            deletionTaskRepository.save(task);
            metrics.recordDeletionFailed();
            log.warn("Media deletion task {} failed on attempt {}", task.getId(), attempts);
        }
    }

    private Duration backoff(int attempts) {
        long multiplier = 1L << Math.min(attempts - 1, 11);
        Duration calculated = INITIAL_BACKOFF.multipliedBy(multiplier);
        return calculated.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : calculated;
    }
}
