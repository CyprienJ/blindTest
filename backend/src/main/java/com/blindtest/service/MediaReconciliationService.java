package com.blindtest.service;

import com.blindtest.model.dto.MediaReconciliationReport;
import com.blindtest.model.dto.MissingMediaObject;
import com.blindtest.model.entity.TrackCovers;
import com.blindtest.repository.MediaDeletionTaskRepository;
import com.blindtest.repository.TrackRepository;
import com.blindtest.service.port.MediaStorage;
import java.time.Clock;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Compares database references with object storage for operator diagnostics. */
@Service
@RequiredArgsConstructor
public class MediaReconciliationService {

    private final TrackRepository trackRepository;
    private final MediaDeletionTaskRepository deletionTaskRepository;
    private final MediaStorage mediaStorage;
    private final MediaOperationalMetrics metrics;
    private final Clock clock;

    /** Produces a point-in-time missing/orphaned object report. */
    @Transactional(readOnly = true)
    public MediaReconciliationReport reconcile() {
        List<ObjectReference> references = Stream.concat(
                trackRepository.findAllProjectedByAudioObjectKeyIsNotNull().stream().
                        map(reference -> new ObjectReference(
                                reference.getId(), reference.getAudioObjectKey())),
                trackRepository.findCoverReferencesByCoverIdIsNotNull().stream().
                        map(reference -> new ObjectReference(
                                reference.getId(), TrackCovers.objectKey(reference.getCoverId())))).
                toList();
        Set<String> referencedKeys = references.stream().
                map(ObjectReference::objectKey).collect(Collectors.toSet());
        Set<String> storedKeys = mediaStorage.listObjectKeys();
        Set<String> pendingDeletionKeys = deletionTaskRepository.findAll().stream().
                map(task -> task.getObjectKey()).collect(Collectors.toSet());

        List<MissingMediaObject> missing = references.stream().
                filter(reference -> !storedKeys.contains(reference.objectKey())).
                map(reference -> new MissingMediaObject(
                        reference.trackId(), fingerprint(reference.objectKey()))).
                sorted(Comparator.comparing(item -> item.trackId().toString())).
                toList();
        List<String> orphaned = storedKeys.stream().
                filter(key -> !referencedKeys.contains(key)).
                filter(key -> !pendingDeletionKeys.contains(key)).
                map(this::fingerprint).
                sorted().
                toList();
        metrics.recordReconciliation(missing.size(), orphaned.size());
        return new MediaReconciliationReport(
                Instant.now(clock), missing.size(), orphaned.size(), missing, orphaned);
    }

    private record ObjectReference(UUID trackId, String objectKey) {
    }

    private String fingerprint(String objectKey) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                    objectKey.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
