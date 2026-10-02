package com.blindtest.controller.admin;

import com.blindtest.model.dto.AdminTrackFilter;
import com.blindtest.model.dto.AdminTrackSort;
import com.blindtest.model.dto.MediaPreviewResponse;
import com.blindtest.model.dto.TrackAnswerAliasRequest;
import com.blindtest.model.dto.TrackAnswerAliasResponse;
import com.blindtest.model.dto.TrackAdminResponse;
import com.blindtest.model.dto.TrackWaveformResponse;
import com.blindtest.model.dto.UpdateTrackRequest;
import com.blindtest.service.AdminTrackService;
import com.blindtest.service.TrackAnswerAliasService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Admin REST controller for track management (all statuses, update, delete, validate, reject). */
@RestController
@RequestMapping("/api/admin/tracks")
@RequiredArgsConstructor
public class AdminTrackController {

    private final AdminTrackService adminTrackService;
    private final TrackAnswerAliasService aliasService;

    /**
     * Lists tracks matching the optional filter query parameters ({@code status}, {@code search},
     * {@code category}, {@code decade}, {@code difficulty}, {@code excludePlaylistId}), ordered by
     * {@code sort} ({@code ADDED} or {@code CATEGORY}) and {@code direction} ({@code ASC} or
     * {@code DESC}).
     */
    @GetMapping
    public Page<TrackAdminResponse> getAllTracks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @ModelAttribute AdminTrackFilter filter,
            @ModelAttribute AdminTrackSort order) {
        return adminTrackService.getAllTracks(page, size, filter, order);
    }

    @GetMapping("/categories")
    public List<String> getCategories() {
        return adminTrackService.getCategories();
    }

    @GetMapping("/{id}/preview-url")
    public MediaPreviewResponse createPreviewUrl(@PathVariable UUID id) {
        return adminTrackService.createPreviewUrl(id);
    }

    @GetMapping("/{id}/waveform")
    public TrackWaveformResponse getWaveform(@PathVariable UUID id) {
        return adminTrackService.getWaveform(id);
    }

    @PutMapping("/{id}")
    public TrackAdminResponse updateTrack(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTrackRequest request) {
        return adminTrackService.updateTrack(id, request);
    }

    @GetMapping("/{trackId}/aliases")
    public List<TrackAnswerAliasResponse> listAliases(@PathVariable UUID trackId) {
        return aliasService.list(trackId);
    }

    @PostMapping("/{trackId}/aliases")
    @ResponseStatus(HttpStatus.CREATED)
    public TrackAnswerAliasResponse createAlias(
            @PathVariable UUID trackId,
            @Valid @RequestBody TrackAnswerAliasRequest request) {
        return aliasService.create(trackId, request);
    }

    @PutMapping("/{trackId}/aliases/{aliasId}")
    public TrackAnswerAliasResponse updateAlias(
            @PathVariable UUID trackId,
            @PathVariable UUID aliasId,
            @Valid @RequestBody TrackAnswerAliasRequest request) {
        return aliasService.update(trackId, aliasId, request);
    }

    @DeleteMapping("/{trackId}/aliases/{aliasId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAlias(
            @PathVariable UUID trackId,
            @PathVariable UUID aliasId) {
        aliasService.delete(trackId, aliasId);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTrack(@PathVariable UUID id) {
        adminTrackService.deleteTrack(id);
    }

    @PostMapping("/{id}/validate")
    public TrackAdminResponse validateTrack(@PathVariable UUID id) {
        return adminTrackService.validateTrack(id);
    }

    @PostMapping("/{id}/reject")
    public TrackAdminResponse rejectTrack(@PathVariable UUID id) {
        return adminTrackService.rejectTrack(id);
    }
}
