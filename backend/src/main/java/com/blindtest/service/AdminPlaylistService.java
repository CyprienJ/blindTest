package com.blindtest.service;

import com.blindtest.model.dto.AddTrackToPlaylistRequest;
import com.blindtest.model.dto.CreatePlaylistRequest;
import com.blindtest.model.dto.PlaylistAdminDetailResponse;
import com.blindtest.model.dto.PlaylistAdminResponse;
import com.blindtest.model.dto.ReorderPlaylistTracksRequest;
import com.blindtest.model.dto.TrackAdminResponse;
import com.blindtest.model.dto.UpdatePlaylistRequest;
import com.blindtest.model.entity.PlaylistEntity;
import com.blindtest.model.entity.PlaylistTrackEntity;
import com.blindtest.model.entity.PlaylistTrackId;
import com.blindtest.model.entity.TrackEntity;
import com.blindtest.model.entity.TrackStatus;
import com.blindtest.model.entity.UserEntity;
import com.blindtest.repository.PlaylistRepository;
import com.blindtest.repository.PlaylistTrackRepository;
import com.blindtest.repository.TrackRepository;
import com.blindtest.repository.UserRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.Comparator;
import java.util.stream.Collectors;
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

    /** Returns all playlists with their playable published-track count. */
    @Transactional(readOnly = true)
    public List<PlaylistAdminResponse> getAllPlaylists() {
        return playlistRepository.findAll().stream().
                map(pl -> PlaylistAdminResponse.from(pl,
                        playlistTrackRepository.countPublishedInPlaylist(
                                pl.getId(), TrackStatus.PUBLISHED))).
                toList();
    }

    /** Returns ordered published tracks only, so unavailable media cannot leak into details. */
    @Transactional(readOnly = true)
    public PlaylistAdminDetailResponse getPlaylist(UUID playlistId) {
        PlaylistEntity playlist = findOrThrow(playlistId);
        List<PlaylistTrackEntity> entries =
                playlistTrackRepository.findPublishedByPlaylistIdOrdered(
                        playlistId, TrackStatus.PUBLISHED);
        return toDetail(playlist, entries);
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
        int count = playlistTrackRepository.countPublishedInPlaylist(
                playlistId, TrackStatus.PUBLISHED);
        return PlaylistAdminResponse.from(playlist, count);
    }

    /** Deletes the playlist and all its track entries. Returns 404 if not found. */
    @Transactional
    public void deletePlaylist(UUID playlistId) {
        findForUpdateOrThrow(playlistId);
        playlistTrackRepository.deleteByIdPlaylistId(playlistId);
        playlistRepository.deleteById(playlistId);
    }

    /** Inserts one published track at a zero-based position and shifts following entries. */
    @Transactional
    public PlaylistAdminDetailResponse addTrack(UUID playlistId, AddTrackToPlaylistRequest request) {
        PlaylistEntity playlist = findForUpdateOrThrow(playlistId);
        List<PlaylistTrackEntity> entries =
                playlistTrackRepository.findAllByPlaylistIdForUpdate(playlistId);
        TrackEntity track = trackRepository.findById(request.getTrackId()).
                orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (track.getStatus() != TrackStatus.PUBLISHED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "PLAYLIST_TRACK_NOT_PUBLISHED");
        }
        PlaylistTrackId key = new PlaylistTrackId(playlistId, track.getId());
        if (playlistTrackRepository.existsById(key)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "PLAYLIST_TRACK_DUPLICATE");
        }
        if (request.getOrder() > entries.size()) {
            throw invalidOrder();
        }
        entries.stream().
                filter(entry -> entry.getTrackOrder() >= request.getOrder()).
                forEach(entry -> entry.setTrackOrder(entry.getTrackOrder() + 1));
        playlistTrackRepository.saveAll(entries);
        playlistTrackRepository.save(new PlaylistTrackEntity(playlist, track, request.getOrder()));
        return toDetail(playlist, playlistTrackRepository.findAllByPlaylistIdOrdered(playlistId));
    }

    /** Removes a track and compacts every following zero-based position. */
    @Transactional
    public void removeTrack(UUID playlistId, UUID trackId) {
        findForUpdateOrThrow(playlistId);
        List<PlaylistTrackEntity> entries =
                playlistTrackRepository.findAllByPlaylistIdForUpdate(playlistId);
        PlaylistTrackEntity removed = entries.stream().
                filter(entry -> entry.getTrack().getId().equals(trackId)).
                findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        playlistTrackRepository.delete(removed);
        entries.remove(removed);
        normalize(entries);
        playlistTrackRepository.saveAll(entries);
    }

    /** Applies a complete ordered ID snapshot atomically; partial or duplicate snapshots are rejected. */
    @Transactional
    public PlaylistAdminDetailResponse reorderTracks(
            UUID playlistId, ReorderPlaylistTracksRequest request) {
        PlaylistEntity playlist = findForUpdateOrThrow(playlistId);
        List<PlaylistTrackEntity> entries =
                playlistTrackRepository.findAllByPlaylistIdForUpdate(playlistId);
        List<UUID> requestedIds = request.getTrackIds();
        Set<UUID> uniqueRequestedIds = new HashSet<>(requestedIds);
        Set<UUID> currentIds = entries.stream().
                map(entry -> entry.getTrack().getId()).collect(Collectors.toSet());
        if (requestedIds.size() != entries.size()
                || uniqueRequestedIds.size() != requestedIds.size()
                || !uniqueRequestedIds.equals(currentIds)) {
            throw invalidOrder();
        }
        for (int position = 0; position < requestedIds.size(); position++) {
            UUID trackId = requestedIds.get(position);
            int requestedPosition = position;
            entries.stream().
                    filter(entry -> entry.getTrack().getId().equals(trackId)).
                    findFirst().orElseThrow(this::invalidOrder).
                    setTrackOrder(requestedPosition);
        }
        playlistTrackRepository.saveAll(entries);
        entries.sort(Comparator.comparingInt(PlaylistTrackEntity::getTrackOrder));
        return toDetail(playlist, entries);
    }

    /** Removes a rejected or deleted track from every playlist and compacts each list atomically. */
    @Transactional
    public void removeTrackFromAllPlaylists(UUID trackId) {
        List<UUID> playlistIds = playlistTrackRepository.findPlaylistIdsByTrackId(trackId).stream().
                sorted().toList();
        for (UUID playlistId : playlistIds) {
            findForUpdateOrThrow(playlistId);
            List<PlaylistTrackEntity> entries =
                    playlistTrackRepository.findAllByPlaylistIdForUpdate(playlistId);
            PlaylistTrackEntity removed = entries.stream().
                    filter(entry -> entry.getTrack().getId().equals(trackId)).
                    findFirst().orElse(null);
            if (removed == null) {
                continue;
            }
            playlistTrackRepository.delete(removed);
            entries.remove(removed);
            normalize(entries);
            playlistTrackRepository.saveAll(entries);
        }
    }

    private void normalize(List<PlaylistTrackEntity> entries) {
        entries.sort(Comparator.comparingInt(PlaylistTrackEntity::getTrackOrder));
        for (int position = 0; position < entries.size(); position++) {
            entries.get(position).setTrackOrder(position);
        }
    }

    private PlaylistAdminDetailResponse toDetail(
            PlaylistEntity playlist, List<PlaylistTrackEntity> entries) {
        List<TrackAdminResponse> tracks = entries.stream().
                map(entry -> TrackAdminResponse.from(entry.getTrack())).toList();
        return new PlaylistAdminDetailResponse(playlist.getId(), playlist.getName(),
                playlist.getDescription(), playlist.getImageUrl(), tracks.size(), tracks);
    }

    private ResponseStatusException invalidOrder() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "PLAYLIST_ORDER_INVALID");
    }

    private PlaylistEntity findForUpdateOrThrow(UUID playlistId) {
        return playlistRepository.findByIdForUpdate(playlistId).
                orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private PlaylistEntity findOrThrow(UUID playlistId) {
        return playlistRepository.findById(playlistId).
                orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
