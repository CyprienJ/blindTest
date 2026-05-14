package com.blindtest.controller.admin;

import com.blindtest.model.dto.TrackAdminResponse;
import com.blindtest.model.dto.UpdateTrackRequest;
import com.blindtest.service.AdminTrackService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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

    @GetMapping
    public Page<TrackAdminResponse> getAllTracks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return adminTrackService.getAllTracks(page, size);
    }

    @PutMapping("/{id}")
    public TrackAdminResponse updateTrack(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTrackRequest request) {
        return adminTrackService.updateTrack(id, request);
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
