package com.blindtest.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.blindtest.model.entity.MediaDeletionTaskEntity;
import com.blindtest.model.entity.TrackEntity;
import com.blindtest.repository.MediaDeletionTaskRepository;
import com.blindtest.repository.TrackRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TrackMediaServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-30T12:00:00Z");

    @Test
    void replacementQueuesPreviousObjectAndPersistsNewReference() {
        TrackRepository trackRepository = mock(TrackRepository.class);
        MediaDeletionTaskRepository deletionRepository = mock(MediaDeletionTaskRepository.class);
        TrackMediaService service = new TrackMediaService(
                trackRepository, deletionRepository, Clock.fixed(NOW, ZoneOffset.UTC));
        TrackEntity track = new TrackEntity();
        track.setAudioObjectKey("tracks/old.mp3");
        when(deletionRepository.findByObjectKey("tracks/old.mp3")).thenReturn(Optional.empty());

        service.replaceMedia(track, "tracks/new.mp3", "a".repeat(64), 123L);

        ArgumentCaptor<MediaDeletionTaskEntity> task =
                ArgumentCaptor.forClass(MediaDeletionTaskEntity.class);
        verify(deletionRepository).save(task.capture());
        assertThat(task.getValue().getObjectKey()).isEqualTo("tracks/old.mp3");
        assertThat(task.getValue().getNextAttemptAt()).isEqualTo(
                LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
        assertThat(track.getAudioObjectKey()).isEqualTo("tracks/new.mp3");
        assertThat(track.getAudioChecksum()).isEqualTo("a".repeat(64));
        assertThat(track.getAudioSizeBytes()).isEqualTo(123L);
        assertThat(track.getAudioUrl()).isNull();
        verify(trackRepository).save(track);
    }

    @Test
    void idempotentReplacementDoesNotQueueCurrentObject() {
        TrackRepository trackRepository = mock(TrackRepository.class);
        MediaDeletionTaskRepository deletionRepository = mock(MediaDeletionTaskRepository.class);
        TrackMediaService service = new TrackMediaService(
                trackRepository, deletionRepository, Clock.fixed(NOW, ZoneOffset.UTC));
        TrackEntity track = new TrackEntity();
        track.setAudioObjectKey("tracks/current.mp3");

        service.replaceMedia(track, "tracks/current.mp3", "b".repeat(64), 456L);

        verify(deletionRepository, never()).save(any());
        verify(trackRepository).save(track);
    }
}
