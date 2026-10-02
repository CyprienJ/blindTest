package com.blindtest.service;

import com.blindtest.config.properties.S3Properties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.blindtest.model.dto.AdminTrackFilter;
import com.blindtest.model.dto.AdminTrackSort;
import com.blindtest.model.dto.MediaPreviewResponse;
import com.blindtest.model.dto.TrackAdminResponse;
import com.blindtest.model.dto.TrackWaveformResponse;
import com.blindtest.model.dto.UpdateTrackRequest;
import com.blindtest.model.entity.ImportJobStatus;
import com.blindtest.model.entity.PlaylistTrackEntity;
import com.blindtest.model.entity.TrackEntity;
import com.blindtest.model.entity.TrackCovers;
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
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
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
    private final ObjectMapper objectMapper;
    private final AdminPlaylistService adminPlaylistService;
    private final TrackCoverService trackCoverService;

    @Value("${app.media-dir:/media}")
    private String mediaDir;

    /** Returns all tracks across all statuses, paginated. */
    public Page<TrackAdminResponse> getAllTracks(int pageNum, int pageSize) {
        return getAllTracks(pageNum, pageSize, (TrackStatus) null, null);
    }

    /** Returns tracks filtered by workflow status and a case-insensitive metadata search. */
    public Page<TrackAdminResponse> getAllTracks(
            int pageNum, int pageSize, TrackStatus status, String search) {
        return getAllTracks(pageNum, pageSize, AdminTrackFilter.of(status, search));
    }

    /** Returns tracks matching every supplied criterion, newest first. */
    public Page<TrackAdminResponse> getAllTracks(int pageNum, int pageSize, AdminTrackFilter filter) {
        return getAllTracks(pageNum, pageSize, filter, AdminTrackSort.defaultOrder());
    }

    /**
     * Returns tracks matching every supplied criterion in the requested order. Every order ends
     * with a stable tie-break so consecutive pages never repeat or skip a track.
     */
    public Page<TrackAdminResponse> getAllTracks(
            int pageNum, int pageSize, AdminTrackFilter filter, AdminTrackSort order) {
        if (filter.difficulty() != null && (filter.difficulty() < 1 || filter.difficulty() > 5)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER");
        }
        // The order is part of the specification: Spring Data's Sort cannot express "uncategorized
        // last" portably. Spring Data drops it from the count query.
        Pageable pageable = PageRequest.of(pageNum, Math.min(pageSize, 100));
        return trackRepository.findAll(specification(filter).and(ordered(order)), pageable).
                map(TrackAdminResponse::from);
    }

    /** Returns the distinct non-blank categories used by tracks, alphabetically. */
    @Transactional(readOnly = true)
    public List<String> getCategories() {
        return trackRepository.findDistinctCategories();
    }

    /**
     * Orders by the requested column. Category ignores case and always lists uncategorized
     * tracks last; ties fall back to newest first, and the identifier keeps paging stable.
     */
    private static Specification<TrackEntity> ordered(AdminTrackSort order) {
        return (root, query, builder) -> {
            boolean ascending = order.effectiveDirection().isAscending();
            Expression<LocalDateTime> createdAt = root.get("createdAt");
            List<Order> orders = new ArrayList<>();
            if (order.effectiveField() == AdminTrackSort.Field.CATEGORY) {
                Expression<String> category = builder.lower(builder.trim(root.get("category")));
                Expression<Integer> uncategorized = builder.<Integer>selectCase().
                        when(builder.or(builder.isNull(category), builder.equal(category, "")), 1).
                        otherwise(0);
                orders.add(builder.asc(uncategorized));
                orders.add(ascending ? builder.asc(category) : builder.desc(category));
                orders.add(builder.desc(createdAt));
            } else {
                orders.add(ascending ? builder.asc(createdAt) : builder.desc(createdAt));
            }
            orders.add(builder.asc(root.get("id")));
            query.orderBy(orders);
            return null;
        };
    }

    private Specification<TrackEntity> specification(AdminTrackFilter filter) {
        Specification<TrackEntity> specification = Specification.where(null);
        if (filter.status() != null) {
            specification = specification.and(
                    (root, query, builder) -> builder.equal(root.get("status"), filter.status()));
        }
        if (filter.search() != null && !filter.search().isBlank()) {
            String pattern = "%"
                    + escapeLike(filter.search().strip().toLowerCase(Locale.ROOT))
                    + "%";
            specification = specification.and((root, query, builder) -> builder.or(
                    builder.like(builder.lower(root.get("title")), pattern, '\\'),
                    builder.like(builder.lower(root.get("artist")), pattern, '\\'),
                    builder.like(builder.lower(root.get("album")), pattern, '\\'),
                    builder.like(builder.lower(root.get("category")), pattern, '\\')));
        }
        if (filter.category() != null && !filter.category().isBlank()) {
            String category = filter.category().strip().toLowerCase(Locale.ROOT);
            specification = specification.and((root, query, builder) ->
                    builder.equal(builder.lower(root.get("category")), category));
        }
        if (filter.decade() != null) {
            specification = specification.and(
                    (root, query, builder) -> builder.equal(root.get("decade"), filter.decade()));
        }
        if (filter.difficulty() != null) {
            specification = specification.and((root, query, builder) ->
                    builder.equal(root.get("difficulty"), filter.difficulty()));
        }
        if (filter.excludePlaylistId() != null) {
            specification = specification.and((root, query, builder) -> {
                var members = query.subquery(UUID.class);
                var entry = members.from(PlaylistTrackEntity.class);
                members.select(entry.get("id").get("trackId")).where(builder.equal(
                        entry.get("id").get("playlistId"), filter.excludePlaylistId()));
                return builder.not(root.get("id").in(members));
            });
        }
        return specification;
    }

    /**
     * Creates a short-lived preview URL for a track's finalized private media.
     *
     * <p>A pending track must come from a completed import; published and rejected tracks
     * can be previewed from the track editor.
     */
    @Transactional(readOnly = true)
    public MediaPreviewResponse createPreviewUrl(UUID id) {
        TrackEntity track = findOrThrow(id);
        String objectKey = track.getAudioObjectKey();
        boolean awaitingImport = track.getStatus() == TrackStatus.PENDING
                && !importJobRepository.existsByTrackIdAndStatus(id, ImportJobStatus.DONE);
        if (awaitingImport
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

    /** Returns the bounded waveform generated for an imported track. */
    @Transactional(readOnly = true)
    public TrackWaveformResponse getWaveform(UUID id) {
        TrackEntity track = findOrThrow(id);
        if (track.getWaveformPeaks() == null || track.getWaveformPeaks().isBlank()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "TRACK_WAVEFORM_UNAVAILABLE");
        }
        try {
            List<Integer> peaks = objectMapper.readValue(
                    track.getWaveformPeaks(), new TypeReference<List<Integer>>() { });
            if (peaks.isEmpty() || peaks.size() > 800
                    || peaks.stream().anyMatch(peak -> peak == null || peak < 0 || peak > 100)) {
                throw new IllegalStateException("Stored waveform data is outside its bounds");
            }
            return new TrackWaveformResponse(track.getDurationSeconds(), List.copyOf(peaks));
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new IllegalStateException("Stored waveform data is invalid", exception);
        }
    }

    /**
     * Updates the non-null metadata fields of the given track and its cover selection.
     * Returns 404 if not found.
     */
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
        if (request.getExcerptDurationSeconds() != null) {
            track.setExcerptDurationSeconds(request.getExcerptDurationSeconds());
        }
        if (request.getImageUrl() != null) {
            track.setImageUrl(request.getImageUrl());
        }
        if (request.getDifficulty() != null) {
            track.setDifficulty(request.getDifficulty());
        }
        if (request.getStreamingLinks() != null) {
            request.getStreamingLinks().applyTo(track);
        }
        applyCover(track, request);
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
        adminPlaylistService.removeTrackFromAllPlaylists(id);
        trackMediaService.enqueueDeletion(track.getAudioObjectKey());
        if (track.getCoverId() != null) {
            trackMediaService.enqueueDeletion(TrackCovers.objectKey(track.getCoverId()));
        }
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
        TrackImportJobEntity job = findRejectableJob(id);
        if (job.getStatus() == ImportJobStatus.REJECTED) {
            return TrackAdminResponse.from(track);
        }
        adminPlaylistService.removeTrackFromAllPlaylists(id);
        track.setStatus(TrackStatus.REJECTED);
        job.setStatus(ImportJobStatus.REJECTED);
        importJobRepository.save(job);
        return TrackAdminResponse.from(trackRepository.save(track));
    }

    private void applyCover(TrackEntity track, UpdateTrackRequest request) {
        if (Boolean.TRUE.equals(request.getRemoveCover())) {
            trackCoverService.removeCover(track);
        } else if (request.getCoverCandidateId() != null) {
            trackCoverService.replaceCover(track, request.getCoverCandidateId());
        }
    }

    private TrackImportJobEntity findRejectableJob(UUID trackId) {
        TrackImportJobEntity job = importJobRepository.findByTrackIdForUpdate(trackId).
                orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT, "IMPORT_NOT_DONE"));
        if (job.getStatus() != ImportJobStatus.DONE
                && job.getStatus() != ImportJobStatus.PUBLISHED
                && job.getStatus() != ImportJobStatus.REJECTED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "INVALID_IMPORT_STATUS_TRANSITION");
        }
        return job;
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

    private String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
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
