package com.blindtest.service;

import com.blindtest.model.dto.PlaylistDetailResponse;
import com.blindtest.model.dto.PlaylistResponse;
import com.blindtest.model.dto.TrackResponse;
import com.blindtest.model.entity.PlaylistEntity;
import com.blindtest.model.entity.PlaylistTrackEntity;
import com.blindtest.model.entity.TrackEntity;
import com.blindtest.model.entity.TrackStatus;
import com.blindtest.repository.PlaylistRepository;
import com.blindtest.repository.PlaylistTrackRepository;
import com.blindtest.repository.TrackRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Service providing public catalogue queries for tracks and playlists. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CatalogueService {

    private final TrackRepository trackRepository;
    private final PlaylistRepository playlistRepository;
    private final PlaylistTrackRepository playlistTrackRepository;

    /** Returns a paginated list of published tracks, optionally filtered by category and decade. */
    public Page<TrackResponse> getTracks(String category, Short decade, int pageNum, int pageSize) {
        Pageable pageable = PageRequest.of(pageNum, Math.min(pageSize, 100));
        Specification<TrackEntity> spec = Specification.where(
                (root, q, cb) -> cb.equal(root.get("status"), TrackStatus.PUBLISHED));
        if (category != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("category"), category));
        }
        if (decade != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("decade"), decade));
        }
        Page<TrackEntity> result = trackRepository.findAll(spec, pageable);
        return result.map(TrackResponse::from);
    }

    /** Returns all playlists with their published track count. */
    public List<PlaylistResponse> getPlaylists() {
        List<PlaylistEntity> playlists = playlistRepository.findAll();
        List<PlaylistResponse> result = new ArrayList<>();
        for (PlaylistEntity p : playlists) {
            int count = playlistTrackRepository.countPublishedInPlaylist(p.getId(), TrackStatus.PUBLISHED);
            result.add(new PlaylistResponse(p.getId(), p.getName(), p.getDescription(), p.getImageUrl(), count));
        }
        return result;
    }

    /** Returns the detail of a playlist with its ordered published tracks. Throws 404 if not found. */
    public PlaylistDetailResponse getPlaylistDetail(UUID id) {
        PlaylistEntity playlist = playlistRepository.findById(id).
                orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        List<PlaylistTrackEntity> pts = playlistTrackRepository.
                findPublishedByPlaylistIdOrdered(id, TrackStatus.PUBLISHED);
        List<TrackResponse> tracks = new ArrayList<>();
        for (PlaylistTrackEntity pt : pts) {
            tracks.add(TrackResponse.from(pt.getTrack()));
        }
        return new PlaylistDetailResponse(
                playlist.getId(), playlist.getName(), playlist.getDescription(),
                playlist.getImageUrl(), tracks.size(), tracks);
    }
}
