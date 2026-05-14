package com.blindtest.controller;

import com.blindtest.model.dto.PlaylistDetailResponse;
import com.blindtest.model.dto.PlaylistResponse;
import com.blindtest.model.dto.TrackResponse;
import com.blindtest.service.CatalogueService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** REST controller exposing public catalogue endpoints for tracks and playlists. */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CatalogueController {

    private final CatalogueService catalogueService;

    @GetMapping("/tracks")
    public Page<TrackResponse> getTracks(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Short decade,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return catalogueService.getTracks(category, decade, page, size);
    }

    @GetMapping("/playlists")
    public List<PlaylistResponse> getPlaylists() {
        return catalogueService.getPlaylists();
    }

    @GetMapping("/playlists/{id}")
    public PlaylistDetailResponse getPlaylistDetail(@PathVariable UUID id) {
        return catalogueService.getPlaylistDetail(id);
    }
}
