package com.blindtest.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.blindtest.model.entity.MediaDeletionTaskEntity;
import com.blindtest.repository.MediaDeletionTaskRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class MediaDeletionWorkerTest {

    @Test
    void processesEveryTaskInTheBoundedDueBatch() {
        Instant now = Instant.parse("2026-07-30T12:00:00Z");
        LocalDateTime dueAt = LocalDateTime.ofInstant(now, ZoneOffset.UTC);
        MediaDeletionTaskRepository repository = mock(MediaDeletionTaskRepository.class);
        MediaDeletionTaskProcessor processor = mock(MediaDeletionTaskProcessor.class);
        MediaDeletionTaskEntity first = task(UUID.randomUUID());
        MediaDeletionTaskEntity second = task(UUID.randomUUID());
        when(repository.findTop25ByNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(dueAt)).
                thenReturn(List.of(first, second));
        MediaDeletionWorker worker = new MediaDeletionWorker(
                repository, processor, Clock.fixed(now, ZoneOffset.UTC));

        worker.processDueTasks();

        verify(processor).process(first.getId());
        verify(processor).process(second.getId());
    }

    private MediaDeletionTaskEntity task(UUID id) {
        MediaDeletionTaskEntity task = new MediaDeletionTaskEntity();
        ReflectionTestUtils.setField(task, "id", id);
        return task;
    }
}
