package com.blindtest.service;

import com.blindtest.model.dto.TrackAdminResponse;
import com.blindtest.model.dto.UpdateTrackRequest;
import com.blindtest.model.entity.TrackEntity;
import com.blindtest.model.entity.TrackStatus;
import com.blindtest.repository.TrackRepository;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
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

    @Value("${app.media-dir:/media}")
    private String mediaDir;

    /** Returns all tracks across all statuses, paginated. */
    public Page<TrackAdminResponse> getAllTracks(int pageNum, int pageSize) {
        Pageable pageable = PageRequest.of(pageNum, Math.min(pageSize, 100));
        return trackRepository.findAll(pageable).map(TrackAdminResponse::from);
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
        if (request.getImageUrl() != null) {
            track.setImageUrl(request.getImageUrl());
        }
        return TrackAdminResponse.from(trackRepository.save(track));
    }

    /** Deletes the track's DB record and attempts to remove its audio file. Returns 404 if not found. */
    @Transactional
    public void deleteTrack(UUID id) {
        TrackEntity track = findOrThrow(id);
        String audioUrl = track.getAudioUrl();
        trackRepository.delete(track);
        deleteAudioFile(audioUrl);
    }

    /** Sets the track status to PUBLISHED. Returns 404 if not found. */
    @Transactional
    public TrackAdminResponse validateTrack(UUID id) {
        TrackEntity track = findOrThrow(id);
        track.setStatus(TrackStatus.PUBLISHED);
        return TrackAdminResponse.from(trackRepository.save(track));
    }

    /** Sets the track status to REJECTED. Returns 404 if not found. */
    @Transactional
    public TrackAdminResponse rejectTrack(UUID id) {
        TrackEntity track = findOrThrow(id);
        track.setStatus(TrackStatus.REJECTED);
        return TrackAdminResponse.from(trackRepository.save(track));
    }

    private TrackEntity findOrThrow(UUID id) {
        return trackRepository.findById(id).
                orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
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
