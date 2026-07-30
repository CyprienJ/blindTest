package com.blindtest.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.blindtest.config.properties.S3Properties;
import com.blindtest.model.entity.ImportJobStatus;
import com.blindtest.model.entity.TrackEntity;
import com.blindtest.model.entity.TrackStatus;
import com.blindtest.repository.TrackImportJobRepository;
import com.blindtest.repository.TrackRepository;
import com.blindtest.service.port.MediaStorage;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class AdminTrackServiceTest {

    private static final UUID TRACK_ID = UUID.fromString("bdb5fd78-49cb-41b7-974a-e9ba9769671b");
    private static final String OBJECT_KEY = "tracks/bdb5fd78/audio.mp3";
    private static final Instant NOW = Instant.parse("2026-07-30T10:15:30Z");

    private TrackRepository trackRepository;
    private TrackImportJobRepository importJobRepository;
    private TrackMediaService trackMediaService;
    private MediaStorage mediaStorage;
    private AdminTrackService service;
    private TrackEntity track;

    @BeforeEach
    void setUp() {
        trackRepository = mock(TrackRepository.class);
        importJobRepository = mock(TrackImportJobRepository.class);
        trackMediaService = mock(TrackMediaService.class);
        mediaStorage = mock(MediaStorage.class);
        S3Properties properties = new S3Properties(
                URI.create("http://minio:9000"), URI.create("https://media.example.test"),
                "eu-west-3", "media", "access", "secret", Duration.ofMinutes(5));
        service = new AdminTrackService(
                trackRepository, importJobRepository, trackMediaService, mediaStorage, properties,
                Clock.fixed(NOW, ZoneOffset.UTC));

        track = mock(TrackEntity.class);
        when(trackRepository.findById(TRACK_ID)).thenReturn(Optional.of(track));
        when(track.getStatus()).thenReturn(TrackStatus.PENDING);
        when(track.getAudioObjectKey()).thenReturn(OBJECT_KEY);
        when(track.getAudioChecksum()).thenReturn("a".repeat(64));
        when(track.getAudioSizeBytes()).thenReturn(1024L);
        when(importJobRepository.existsByTrackIdAndStatus(TRACK_ID, ImportJobStatus.DONE)).
                thenReturn(true);
        when(mediaStorage.exists(OBJECT_KEY)).thenReturn(true);
        when(mediaStorage.presignedGetUrl(OBJECT_KEY)).
                thenReturn(URI.create("https://media.example.test/audio.mp3?signature=secret"));
    }

    @Test
    void createsShortLivedUrlForCompletedPendingImport() {
        var response = service.createPreviewUrl(TRACK_ID);

        assertThat(response.url()).hasToString(
                "https://media.example.test/audio.mp3?signature=secret");
        assertThat(response.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(5)));
    }

    @Test
    void rejectsTrackWhoseImportIsNotDone() {
        when(importJobRepository.existsByTrackIdAndStatus(TRACK_ID, ImportJobStatus.DONE)).
                thenReturn(false);

        assertConflict();
        verify(mediaStorage, never()).presignedGetUrl(OBJECT_KEY);
    }

    @Test
    void rejectsPublishedTrack() {
        when(track.getStatus()).thenReturn(TrackStatus.PUBLISHED);

        assertConflict();
        verify(mediaStorage, never()).presignedGetUrl(OBJECT_KEY);
    }

    @Test
    void rejectsIncompleteMediaMetadata() {
        when(track.getAudioChecksum()).thenReturn(null);

        assertConflict();
        verify(mediaStorage, never()).exists(OBJECT_KEY);
    }

    @Test
    void rejectsMissingObject() {
        when(mediaStorage.exists(OBJECT_KEY)).thenReturn(false);

        assertConflict();
        verify(mediaStorage, never()).presignedGetUrl(OBJECT_KEY);
    }

    @Test
    void deleteQueuesObjectBeforeRemovingTrackRecord() {
        service.deleteTrack(TRACK_ID);

        verify(trackMediaService).enqueueDeletion(OBJECT_KEY);
        verify(trackRepository).delete(track);
    }

    private void assertConflict() {
        assertThatThrownBy(() -> service.createPreviewUrl(TRACK_ID)).
                isInstanceOf(ResponseStatusException.class).
                satisfies(exception -> assertThat(((ResponseStatusException) exception).
                        getStatusCode().value()).isEqualTo(409));
    }
}
