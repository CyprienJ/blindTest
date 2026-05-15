package com.blindtest.service;

import com.blindtest.model.dto.AddTrackToPlaylistRequest;
import com.blindtest.model.dto.CreatePlaylistRequest;
import com.blindtest.model.dto.PlaylistAdminDetailResponse;
import com.blindtest.model.dto.PlaylistAdminResponse;
import com.blindtest.model.dto.TrackAdminResponse;
import com.blindtest.model.dto.UpdatePlaylistRequest;
import com.blindtest.model.entity.PlaylistEntity;
import com.blindtest.model.entity.PlaylistTrackEntity;
import com.blindtest.model.entity.PlaylistTrackId;
import com.blindtest.model.entity.TrackEntity;
import com.blindtest.model.entity.UserEntity;
import com.blindtest.repository.PlaylistRepository;
import com.blindtest.repository.PlaylistTrackRepository;
import com.blindtest.repository.TrackRepository;
import com.blindtest.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Service managing admin CRUD operations for playlists and their track associations. */
@Service
@RequiredArgsConstructor
public class AdminPlaylistService {

    private final PlaylistRepository playlistRepository;
    private final PlaylistTrackRepository playlistTrackRepository;
    private final TrackRepository trackRepository;
    private final UserRepository userRepository;

    /** Returns all playlists with total track count (all statuses). */
    @Transactional(readOnly = true)
    public List<PlaylistAdminResponse> getAllPlaylists() {
        return playlistRepository.findAll().stream().
                map(pl -> PlaylistAdminResponse.from(pl,
                        playlistTrackRepository.countByIdPlaylistId(pl.getId()))).
                toList();
    }

    /** Returns the playlist detail with all tracks ordered by position. Returns 404 if not found. */
    @Transactional(readOnly = true)
    public PlaylistAdminDetailResponse getPlaylist(UUID playlistId) {
        PlaylistEntity playlist = findOrThrow(playlistId);
        List<PlaylistTrackEntity> entries =
                playlistTrackRepository.findAllByPlaylistIdOrdered(playlistId);
        List<TrackAdminResponse> tracks = entries.stream().
                map(pt -> TrackAdminResponse.from(pt.getTrack())).
                toList();
        return new PlaylistAdminDetailResponse(playlist.getId(), playlist.getName(),
                playlist.getDescription(), playlist.getImageUrl(), tracks.size(), tracks);
    }

    /** Creates a new playlist owned by the given admin. Returns 401 if admin not found. */
    @Transactional
    public PlaylistAdminResponse createPlaylist(CreatePlaylistRequest request, UUID adminId) {
        UserEntity admin = userRepository.findById(adminId).
                orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        PlaylistEntity playlist = new PlaylistEntity();
        playlist.setName(request.getName());
        playlist.setDescription(request.getDescription());
        playlist.setImageUrl(request.getImageUrl());
        playlist.setCreatedBy(admin);
        playlist = playlistRepository.save(playlist);
        return PlaylistAdminResponse.from(playlist, 0);
    }

    /** Partially updates playlist metadata; fields left null are unchanged. Returns 404 if not found. */
    @Transactional
    public PlaylistAdminResponse updatePlaylist(UUID playlistId, UpdatePlaylistRequest request) {
        PlaylistEntity playlist = findOrThrow(playlistId);
        if (request.getName() != null) {
            playlist.setName(request.getName());
        }
        if (request.getDescription() != null) {
            playlist.setDescription(request.getDescription());
        }
        if (request.getImageUrl() != null) {
            playlist.setImageUrl(request.getImageUrl());
        }
        playlist = playlistRepository.save(playlist);
        int count = playlistTrackRepository.countByIdPlaylistId(playlistId);
        return PlaylistAdminResponse.from(playlist, count);
    }

    /** Deletes the playlist and all its track entries. Returns 404 if not found. */
    @Transactional
    public void deletePlaylist(UUID playlistId) {
        findOrThrow(playlistId);
        playlistTrackRepository.deleteByIdPlaylistId(playlistId);
        playlistRepository.deleteById(playlistId);
    }

    /** Adds or updates a track's position in the playlist; returns the updated detail. Returns 404 if not found. */
    @Transactional
    public PlaylistAdminDetailResponse addTrack(UUID playlistId, AddTrackToPlaylistRequest request) {
        PlaylistEntity playlist = findOrThrow(playlistId);
        TrackEntity track = trackRepository.findById(request.getTrackId()).
                orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        playlistTrackRepository.save(new PlaylistTrackEntity(playlist, track, request.getOrder()));
        List<PlaylistTrackEntity> entries =
                playlistTrackRepository.findAllByPlaylistIdOrdered(playlistId);
        List<TrackAdminResponse> tracks = entries.stream().
                map(pt -> TrackAdminResponse.from(pt.getTrack())).
                toList();
        return new PlaylistAdminDetailResponse(playlist.getId(), playlist.getName(),
                playlist.getDescription(), playlist.getImageUrl(), tracks.size(), tracks);
    }

    /** Removes a track from the playlist. Returns 404 if playlist or track entry not found. */
    @Transactional
    public void removeTrack(UUID playlistId, UUID trackId) {
        findOrThrow(playlistId);
        PlaylistTrackId key = new PlaylistTrackId(playlistId, trackId);
        if (!playlistTrackRepository.existsById(key)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        playlistTrackRepository.deleteById(key);
    }

    private PlaylistEntity findOrThrow(UUID playlistId) {
        return playlistRepository.findById(playlistId).
                orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
