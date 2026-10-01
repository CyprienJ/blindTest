package com.blindtest.service.port;

import com.blindtest.model.entity.CoverSource;
import java.net.URI;
import java.util.Objects;

/**
 * One cover found by a provider.
 *
 * @param thumbnailUrl small image (about 250 px) shown while choosing
 * @param imageUrl larger image downloaded when the cover is chosen
 */
public record CoverArtResult(
        CoverSource source,
        String album,
        String artist,
        URI thumbnailUrl,
        URI imageUrl) {

    /** Requires the source and both image URLs. */
    public CoverArtResult {
        Objects.requireNonNull(source, "source is required");
        Objects.requireNonNull(thumbnailUrl, "thumbnailUrl is required");
        Objects.requireNonNull(imageUrl, "imageUrl is required");
        album = album == null ? "" : album.strip();
        artist = artist == null ? "" : artist.strip();
    }
}
