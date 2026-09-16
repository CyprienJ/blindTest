package com.blindtest.controller.admin;

import com.blindtest.model.dto.AddTrackToPlaylistRequest;
import com.blindtest.model.dto.CreatePlaylistRequest;
import com.blindtest.model.dto.PlaylistAdminDetailResponse;
import com.blindtest.model.dto.PlaylistAdminResponse;
import com.blindtest.model.dto.ReorderPlaylistTracksRequest;
import com.blindtest.model.dto.UpdatePlaylistRequest;
import com.blindtest.service.AdminPlaylistService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Admin REST controller for playlist CRUD and track association management. */
@RestController
@RequestMapping("/api/admin/playlists")
@RequiredArgsConstructor
public class AdminPlaylistController {

    private final AdminPlaylistService adminPlaylistService;

    @GetMapping
    public List<PlaylistAdminResponse> getAllPlaylists() {
        return adminPlaylistService.getAllPlaylists();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PlaylistAdminResponse createPlaylist(
            @Valid @RequestBody CreatePlaylistRequest request,
            Authentication authentication) {
        UUID adminId = UUID.fromString(authentication.getName());
        return adminPlaylistService.createPlaylist(request, adminId);
    }

    @GetMapping("/{id}")
    public PlaylistAdminDetailResponse getPlaylist(@PathVariable UUID id) {
        return adminPlaylistService.getPlaylist(id);
    }

    @PutMapping("/{id}")
    public PlaylistAdminResponse updatePlaylist(
            @PathVariable UUID id,
            @RequestBody UpdatePlaylistRequest request) {
        return adminPlaylistService.updatePlaylist(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePlaylist(@PathVariable UUID id) {
        adminPlaylistService.deletePlaylist(id);
    }

    @PostMapping("/{id}/tracks")
    public PlaylistAdminDetailResponse addTrack(
            @PathVariable UUID id,
            @Valid @RequestBody AddTrackToPlaylistRequest request) {
        return adminPlaylistService.addTrack(id, request);
    }

    @DeleteMapping("/{id}/tracks/{trackId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeTrack(@PathVariable UUID id, @PathVariable UUID trackId) {
        adminPlaylistService.removeTrack(id, trackId);
    }

    @PutMapping("/{id}/tracks/order")
    public PlaylistAdminDetailResponse reorderTracks(
            @PathVariable UUID id,
            @Valid @RequestBody ReorderPlaylistTracksRequest request) {
        return adminPlaylistService.reorderTracks(id, request);
    }
}
