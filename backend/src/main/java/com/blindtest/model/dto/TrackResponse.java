package com.blindtest.model.dto;

import com.blindtest.model.entity.TrackEntity;
import java.util.UUID;

/**
 * Immutable public metadata snapshot; excludes media credentials and provider details.
 *
 * @param title canonical primary answer: film title for film tracks, music title otherwise
 */
public record TrackResponse(
        UUID id,
        String title,
        String artist,
        String album,
        String composer,
        String work,
        String category,
        Short decade,
        Integer durationSeconds,
        String imageUrl,
        Short difficulty) {

    /** Creates a public response DTO from the given entity, excluding sensitive fields. */
    public static TrackResponse from(TrackEntity entity) {
        return new TrackResponse(
                entity.getId(),
                entity.getTitle(),
                entity.getArtist(),
                entity.getAlbum(),
                entity.getComposer(),
                entity.getWork(),
                entity.getCategory(),
                entity.getDecade(),
                entity.getDurationSeconds(),
                entity.getImageUrl(),
                entity.getDifficulty());
    }
}
