package com.blindtest.service;

import com.blindtest.config.properties.ImportProperties;
import com.blindtest.model.entity.TrackCovers;
import com.blindtest.model.entity.TrackEntity;
import com.blindtest.service.port.MediaStorage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

/** Stores, replaces, removes, and reads the administrator-selected cover of a track. */
@Service
@RequiredArgsConstructor
@Slf4j
public class TrackCoverService {

    private static final String JPEG = "image/jpeg";

    private final CoverCandidateRegistry registry;
    private final CoverSuggestionService suggestionService;
    private final CoverImageNormalizer normalizer;
    private final MediaStorage mediaStorage;
    private final TrackMediaService trackMediaService;
    private final ImportProperties importProperties;

    /**
     * Stores the chosen candidate as a new private object and points the track at it.
     *
     * <p>The replaced cover is queued for deletion. If the surrounding transaction rolls back,
     * the new object is deleted again on a best-effort basis.
     *
     * @throws ResponseStatusException {@code COVER_SUGGESTION_EXPIRED} when the candidate is
     *     unknown, or an image error when it cannot be downloaded or decoded
     */
    public void replaceCover(TrackEntity track, UUID candidateId) {
        CoverCandidate candidate = registry.find(candidateId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.CONFLICT, "COVER_SUGGESTION_EXPIRED"));
        byte[] jpeg = candidate.uploaded()
                ? candidate.image()
                : normalizer.normalize(suggestionService.download(candidate.imageUrl()));
        UUID coverId = UUID.randomUUID();
        String objectKey = TrackCovers.objectKey(coverId);
        store(objectKey, jpeg);
        deleteOnRollback(objectKey);
        removeCover(track);
        track.setCoverId(coverId);
        track.setCoverSource(candidate.source());
    }

    /** Clears the track's cover and queues its object for deletion. */
    public void removeCover(TrackEntity track) {
        if (track.getCoverId() != null) {
            trackMediaService.enqueueDeletion(TrackCovers.objectKey(track.getCoverId()));
        }
        track.setCoverId(null);
        track.setCoverSource(null);
    }

    /** Returns the stored JPEG of a cover, or empty when it does not exist. */
    public Optional<byte[]> read(UUID coverId) {
        return mediaStorage.read(TrackCovers.objectKey(coverId));
    }

    private void store(String objectKey, byte[] jpeg) {
        Path file = null;
        try {
            Path directory = Files.createDirectories(importProperties.tempDir().resolve("covers"));
            file = Files.createTempFile(directory, "stored-", ".jpg");
            Files.write(file, jpeg);
            mediaStorage.store(objectKey, file, JPEG);
        } catch (IOException exception) {
            throw new IllegalStateException("Cover workspace is unavailable", exception);
        } finally {
            if (file != null) {
                try {
                    Files.deleteIfExists(file);
                } catch (IOException exception) {
                    log.warn("Could not delete temporary cover file {}", file.getFileName());
                }
            }
        }
    }

    private void deleteOnRollback(String objectKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_ROLLED_BACK) {
                    return;
                }
                try {
                    mediaStorage.delete(objectKey);
                } catch (RuntimeException exception) {
                    // Reconciliation reports the orphan; the update itself already failed.
                    log.warn("Could not delete cover object of a rolled-back update");
                }
            }
        });
    }
}
