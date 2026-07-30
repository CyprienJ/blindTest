package com.blindtest.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.blindtest.model.entity.MediaDeletionTaskEntity;
import com.blindtest.repository.MediaDeletionTaskRepository;
import com.blindtest.service.port.MediaStorage;
import com.blindtest.service.port.MediaStorageException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class MediaDeletionTaskProcessorTest {

    private static final Instant NOW = Instant.parse("2026-07-30T12:00:00Z");
    private static final UUID TASK_ID = UUID.fromString("cb7e2b49-bf1c-44c6-9421-76bbb99c01a0");

    @Test
    void transientFailureKeepsTaskAndSchedulesBackoffWithoutStoringErrorDetails() {
        Fixture fixture = fixture();
        doThrow(new MediaStorageException("delete", new RuntimeException("secret endpoint"))).
                when(fixture.storage()).delete("tracks/old.mp3");

        fixture.processor().process(TASK_ID);

        assertThat(fixture.task().getAttemptCount()).isEqualTo(1);
        assertThat(fixture.task().getNextAttemptAt()).isEqualTo(
                LocalDateTime.ofInstant(NOW.plusSeconds(30), ZoneOffset.UTC));
        assertThat(fixture.task().getLastError()).isEqualTo("MediaStorageException");
        verify(fixture.repository()).save(fixture.task());
        verify(fixture.repository(), never()).delete(fixture.task());
        verify(fixture.metrics()).recordDeletionFailed();
    }

    @Test
    void successfulRetryDeletesObjectAndOutboxTask() {
        Fixture fixture = fixture();
        fixture.task().setAttemptCount(2);

        fixture.processor().process(TASK_ID);

        verify(fixture.storage()).delete("tracks/old.mp3");
        verify(fixture.repository()).delete(fixture.task());
        verify(fixture.metrics()).recordDeletionSucceeded();
    }

    private Fixture fixture() {
        MediaDeletionTaskRepository repository = mock(MediaDeletionTaskRepository.class);
        MediaStorage storage = mock(MediaStorage.class);
        MediaOperationalMetrics metrics = mock(MediaOperationalMetrics.class);
        MediaDeletionTaskEntity task = new MediaDeletionTaskEntity();
        ReflectionTestUtils.setField(task, "id", TASK_ID);
        task.setObjectKey("tracks/old.mp3");
        task.setNextAttemptAt(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
        when(repository.findById(TASK_ID)).thenReturn(Optional.of(task));
        MediaDeletionTaskProcessor processor = new MediaDeletionTaskProcessor(
                repository, storage, metrics, Clock.fixed(NOW, ZoneOffset.UTC));
        return new Fixture(processor, repository, storage, metrics, task);
    }

    private record Fixture(
            MediaDeletionTaskProcessor processor,
            MediaDeletionTaskRepository repository,
            MediaStorage storage,
            MediaOperationalMetrics metrics,
            MediaDeletionTaskEntity task) {
    }
}
