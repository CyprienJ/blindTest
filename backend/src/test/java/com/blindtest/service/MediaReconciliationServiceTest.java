package com.blindtest.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.blindtest.model.entity.MediaDeletionTaskEntity;
import com.blindtest.repository.MediaDeletionTaskRepository;
import com.blindtest.repository.TrackMediaReference;
import com.blindtest.repository.TrackRepository;
import com.blindtest.service.port.MediaStorage;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MediaReconciliationServiceTest {

    @Test
    void reportsMissingAndOrphanedObjectsButExcludesPendingDeletions() {
        TrackRepository trackRepository = mock(TrackRepository.class);
        MediaDeletionTaskRepository deletionRepository = mock(MediaDeletionTaskRepository.class);
        MediaStorage storage = mock(MediaStorage.class);
        MediaOperationalMetrics metrics = mock(MediaOperationalMetrics.class);
        TrackMediaReference present = reference(UUID.randomUUID(), "tracks/present.mp3");
        TrackMediaReference missing = reference(UUID.randomUUID(), "tracks/missing.mp3");
        MediaDeletionTaskEntity pending = new MediaDeletionTaskEntity();
        pending.setObjectKey("tracks/pending-delete.mp3");
        when(trackRepository.findAllProjectedByAudioObjectKeyIsNotNull()).
                thenReturn(List.of(present, missing));
        when(deletionRepository.findAll()).thenReturn(List.of(pending));
        when(storage.listObjectKeys()).thenReturn(Set.of(
                "tracks/present.mp3", "tracks/orphan.mp3", "tracks/pending-delete.mp3"));
        Instant now = Instant.parse("2026-07-30T12:00:00Z");
        MediaReconciliationService service = new MediaReconciliationService(
                trackRepository, deletionRepository, storage, metrics,
                Clock.fixed(now, ZoneOffset.UTC));

        var report = service.reconcile();

        assertThat(report.checkedAt()).isEqualTo(now);
        assertThat(report.missingCount()).isEqualTo(1);
        assertThat(report.missingObjects()).singleElement().satisfies(item -> {
            assertThat(item.trackId()).isEqualTo(missing.getId());
            assertThat(item.objectFingerprint()).hasSize(16);
        });
        assertThat(report.orphanedCount()).isEqualTo(1);
        assertThat(report.orphanedObjectFingerprints()).singleElement().asString().hasSize(16);
        verify(metrics).recordReconciliation(1, 1);
    }

    private TrackMediaReference reference(UUID trackId, String objectKey) {
        TrackMediaReference reference = mock(TrackMediaReference.class);
        when(reference.getId()).thenReturn(trackId);
        when(reference.getAudioObjectKey()).thenReturn(objectKey);
        return reference;
    }
}
