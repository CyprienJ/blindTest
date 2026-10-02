package com.blindtest.model.dto;

import com.blindtest.model.entity.TrackStatus;
import java.util.UUID;

/**
 * Optional criteria of the administrator track list; {@code null} means "any".
 *
 * @param search case-insensitive text matched against title, artist, album, and category
 * @param category exact category, ignoring case
 * @param difficulty administrator difficulty from 1 to 5
 * @param excludePlaylistId hides tracks already in this playlist
 */
public record AdminTrackFilter(
        TrackStatus status,
        String search,
        String category,
        Short decade,
        Short difficulty,
        UUID excludePlaylistId) {

    /** Filters by status and text only. */
    public static AdminTrackFilter of(TrackStatus status, String search) {
        return new AdminTrackFilter(status, search, null, null, null, null);
    }
}
