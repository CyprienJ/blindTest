package com.blindtest.controller;

import com.blindtest.service.TrackCoverService;
import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Serves stored covers to {@code <img>} elements, which cannot send a bearer token.
 *
 * <p>A cover ID is random and changes with every new cover. It reaches players only in the
 * round reveal, so the ID itself grants access and the image can be cached forever.
 */
@RestController
@RequestMapping("/api/covers")
@RequiredArgsConstructor
public class CoverController {

    private final TrackCoverService trackCoverService;

    /** Returns the stored JPEG of a cover. */
    @GetMapping("/{coverId}")
    public ResponseEntity<byte[]> cover(@PathVariable UUID coverId) {
        byte[] jpeg = trackCoverService.read(coverId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND));
        return ResponseEntity.ok().
                contentType(MediaType.IMAGE_JPEG).
                cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable()).
                body(jpeg);
    }
}
