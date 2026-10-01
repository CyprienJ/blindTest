package com.blindtest.service;

import com.blindtest.model.entity.CoverSource;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;

/**
 * A cover the administrator may choose, kept server-side so clients only ever send its ID.
 *
 * @param thumbnailUrl provider thumbnail, or {@code null} for an uploaded image
 * @param imageUrl provider image downloaded on selection, or {@code null} for an upload
 * @param image normalized JPEG of an uploaded image, or {@code null} for a provider result
 */
record CoverCandidate(
        UUID id,
        CoverSource source,
        String album,
        String artist,
        URI thumbnailUrl,
        URI imageUrl,
        byte[] image,
        Instant expiresAt) {

    boolean uploaded() {
        return image != null;
    }
}
