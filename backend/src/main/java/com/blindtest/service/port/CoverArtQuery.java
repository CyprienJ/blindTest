package com.blindtest.service.port;

import java.util.Objects;

/**
 * Track metadata used to search cover art.
 *
 * @param album optional album name; {@code null} when unknown
 */
public record CoverArtQuery(String title, String artist, String album) {

    /** Trims values and normalizes a blank album to {@code null}. */
    public CoverArtQuery {
        title = Objects.requireNonNull(title, "title is required").strip();
        artist = Objects.requireNonNull(artist, "artist is required").strip();
        album = album == null || album.isBlank() ? null : album.strip();
    }

    /** Returns whether an album name was supplied. */
    public boolean hasAlbum() {
        return album != null;
    }
}
