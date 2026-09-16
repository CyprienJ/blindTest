package com.blindtest.service;

import com.blindtest.model.entity.MediaDeletionTaskEntity;
import com.blindtest.model.entity.TrackEntity;
import com.blindtest.model.entity.TrackStatus;
import com.blindtest.repository.MediaDeletionTaskRepository;
import com.blindtest.repository.TrackRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Atomically changes durable track media references and queues obsolete objects. */
@Service
@RequiredArgsConstructor
public class TrackMediaService {

    private final TrackRepository trackRepository;
    private final MediaDeletionTaskRepository deletionTaskRepository;
    private final Clock clock;

    /** Persists finalized media and queues a replaced object in the same transaction. */
    @Transactional
    public void replaceMedia(
            TrackEntity track,
            String objectKey,
            String checksum,
            long sizeBytes) {
        String previousObjectKey = track.getAudioObjectKey();
        if (previousObjectKey != null && !previousObjectKey.equals(objectKey)) {
            enqueueDeletion(previousObjectKey);
        }
        track.setAudioUrl(null);
        track.setAudioObjectKey(objectKey);
        track.setAudioChecksum(checksum);
        track.setAudioSizeBytes(sizeBytes);
        track.setStatus(TrackStatus.PENDING);
        trackRepository.save(track);
    }

    /** Queues an object for idempotent asynchronous deletion. */
    public void enqueueDeletion(String objectKey) {
        if (objectKey == null || deletionTaskRepository.findByObjectKey(objectKey).isPresent()) {
            return;
        }
        MediaDeletionTaskEntity task = new MediaDeletionTaskEntity();
        task.setObjectKey(objectKey);
        task.setNextAttemptAt(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        deletionTaskRepository.save(task);
    }
}
