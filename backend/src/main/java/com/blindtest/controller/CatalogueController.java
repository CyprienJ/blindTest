package com.blindtest.controller;

import com.blindtest.model.dto.PlaylistDetailResponse;
import com.blindtest.model.dto.PlaylistResponse;
import com.blindtest.model.dto.TrackResponse;
import com.blindtest.service.CatalogueService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** REST controller exposing public catalogue endpoints for tracks and playlists. */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Validated
public class CatalogueController {

    private static final CacheControl PUBLIC_CACHE = CacheControl.maxAge(Duration.ofSeconds(60)).
            staleWhileRevalidate(Duration.ofMinutes(5)).cachePublic();

    private final CatalogueService catalogueService;

    @GetMapping("/tracks")
    public ResponseEntity<Page<TrackResponse>> getTracks(
            @RequestParam(required = false, name = "q") String query,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Short decade,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok().cacheControl(PUBLIC_CACHE).
                body(catalogueService.getTracks(query, category, decade, page, size));
    }

    @GetMapping("/playlists")
    public ResponseEntity<List<PlaylistResponse>> getPlaylists() {
        return ResponseEntity.ok().cacheControl(PUBLIC_CACHE).
                body(catalogueService.getPlaylists());
    }

    @GetMapping("/playlists/{id}")
    public ResponseEntity<PlaylistDetailResponse> getPlaylistDetail(@PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(PUBLIC_CACHE).
                body(catalogueService.getPlaylistDetail(id));
    }
}
