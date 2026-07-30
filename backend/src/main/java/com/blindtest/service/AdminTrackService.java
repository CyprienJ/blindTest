package com.blindtest.service;

import com.blindtest.config.properties.S3Properties;
import com.blindtest.model.dto.MediaPreviewResponse;
import com.blindtest.model.dto.TrackAdminResponse;
import com.blindtest.model.dto.UpdateTrackRequest;
import com.blindtest.model.entity.ImportJobStatus;
import com.blindtest.model.entity.TrackEntity;
import com.blindtest.model.entity.TrackImportJobEntity;
import com.blindtest.model.entity.TrackPublicationPolicy;
import com.blindtest.model.entity.TrackStatus;
import com.blindtest.repository.TrackImportJobRepository;
import com.blindtest.repository.TrackRepository;
import com.blindtest.service.port.MediaStorage;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Service for admin track management operations. */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminTrackService {

    private final TrackRepository trackRepository;
    private final TrackImportJobRepository importJobRepository;
    private final TrackMediaService trackMediaService;
    private final MediaStorage mediaStorage;
    private final S3Properties s3Properties;
    private final Clock clock;

    @Value("${app.media-dir:/media}")
    private String mediaDir;

    /** Returns all tracks across all statuses, paginated. */
    public Page<TrackAdminResponse> getAllTracks(int pageNum, int pageSize) {
        Pageable pageable = PageRequest.of(pageNum, Math.min(pageSize, 100));
        return trackRepository.findAll(pageable).map(TrackAdminResponse::from);
    }

    /** Creates a short-lived preview URL for a completed, unpublished import. */
    @Transactional(readOnly = true)
    public MediaPreviewResponse createPreviewUrl(UUID id) {
        TrackEntity track = findOrThrow(id);
        String objectKey = track.getAudioObjectKey();
        boolean completedImport = importJobRepository.existsByTrackIdAndStatus(
                id, ImportJobStatus.DONE);
        if (track.getStatus() != TrackStatus.PENDING || !completedImport
                || objectKey == null || track.getAudioChecksum() == null
                || track.getAudioSizeBytes() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "CONFLICT");
        }
        if (!mediaStorage.exists(objectKey)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "CONFLICT");
        }
        Instant expiresAt = Instant.now(clock).plus(s3Properties.presignedUrlTtl());
        URI url = mediaStorage.presignedGetUrl(objectKey);
        return new MediaPreviewResponse(url, expiresAt);
    }

    /** Updates the non-null metadata fields of the given track. Returns 404 if not found. */
    @Transactional
    public TrackAdminResponse updateTrack(UUID id, UpdateTrackRequest request) {
        TrackEntity track = findOrThrow(id);
        if (request.getTitle() != null) {
            track.setTitle(request.getTitle());
        }
        if (request.getArtist() != null) {
            track.setArtist(request.getArtist());
        }
        if (request.getAlbum() != null) {
            track.setAlbum(request.getAlbum());
        }
        if (request.getComposer() != null) {
            track.setComposer(request.getComposer());
        }
        if (request.getWork() != null) {
            track.setWork(request.getWork());
        }
        if (request.getCategory() != null) {
            track.setCategory(request.getCategory());
        }
        if (request.getDecade() != null) {
            track.setDecade(request.getDecade());
        }
        if (request.getDurationSeconds() != null) {
            track.setDurationSeconds(request.getDurationSeconds());
        }
        if (request.getExcerptStartSeconds() != null) {
            track.setExcerptStartSeconds(request.getExcerptStartSeconds());
        }
        if (request.getImageUrl() != null) {
            track.setImageUrl(request.getImageUrl());
        }
        if (track.getStatus() == TrackStatus.PUBLISHED) {
            validatePublishable(track);
        }
        return TrackAdminResponse.from(trackRepository.save(track));
    }

    /** Deletes a track and durably queues object cleanup. Returns 404 if not found. */
    @Transactional
    public void deleteTrack(UUID id) {
        TrackEntity track = findOrThrow(id);
        String audioUrl = track.getAudioUrl();
        trackMediaService.enqueueDeletion(track.getAudioObjectKey());
        trackRepository.delete(track);
        deleteAudioFile(audioUrl);
    }

    /** Sets the track status to PUBLISHED. Returns 404 if not found. */
    @Transactional
    public TrackAdminResponse validateTrack(UUID id) {
        TrackEntity track = findOrThrow(id);
        TrackImportJobEntity job = findReviewableJob(id, ImportJobStatus.PUBLISHED);
        if (job.getStatus() == ImportJobStatus.PUBLISHED) {
            return TrackAdminResponse.from(track);
        }
        validatePublishable(track);
        track.setStatus(TrackStatus.PUBLISHED);
        job.setStatus(ImportJobStatus.PUBLISHED);
        importJobRepository.save(job);
        return TrackAdminResponse.from(trackRepository.save(track));
    }

    /** Sets the track status to REJECTED. Returns 404 if not found. */
    @Transactional
    public TrackAdminResponse rejectTrack(UUID id) {
        TrackEntity track = findOrThrow(id);
        TrackImportJobEntity job = findReviewableJob(id, ImportJobStatus.REJECTED);
        if (job.getStatus() == ImportJobStatus.REJECTED) {
            return TrackAdminResponse.from(track);
        }
        track.setStatus(TrackStatus.REJECTED);
        job.setStatus(ImportJobStatus.REJECTED);
        importJobRepository.save(job);
        return TrackAdminResponse.from(trackRepository.save(track));
    }

    private TrackImportJobEntity findReviewableJob(
            UUID trackId, ImportJobStatus idempotentStatus) {
        TrackImportJobEntity job = importJobRepository.findByTrackIdForUpdate(trackId).
                orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT, "IMPORT_NOT_DONE"));
        if (job.getStatus() != ImportJobStatus.DONE
                && job.getStatus() != idempotentStatus) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "INVALID_IMPORT_STATUS_TRANSITION");
        }
        return job;
    }

    private TrackEntity findOrThrow(UUID id) {
        return trackRepository.findById(id).
                orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private void validatePublishable(TrackEntity track) {
        if (!TrackPublicationPolicy.hasPlayableExcerpt(track)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "TRACK_EXCERPT_INVALID");
        }
        if (!TrackPublicationPolicy.isComplete(track)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "TRACK_METADATA_INCOMPLETE");
        }
    }

    private void deleteAudioFile(String audioUrl) {
        Path filePath = resolveAudioPath(audioUrl);
        if (filePath != null) {
            try {
                Files.deleteIfExists(filePath);
            } catch (IOException e) {
                log.warn("Could not delete audio file {}: {}", filePath, e.getMessage());
            }
        }
    }

    private Path resolveAudioPath(String audioUrl) {
        if (audioUrl == null) {
            return null;
        }
        String path = audioUrl;
        if (path.startsWith("http://") || path.startsWith("https://")) {
            try {
                path = new URI(audioUrl).getPath();
            } catch (URISyntaxException e) {
                return null;
            }
        }
        if (!path.startsWith("/media/")) {
            return null;
        }
        String relativePath = path.substring("/media/".length());
        if (relativePath.isEmpty()) {
            return null;
        }
        return Path.of(mediaDir).resolve(relativePath);
    }
}
