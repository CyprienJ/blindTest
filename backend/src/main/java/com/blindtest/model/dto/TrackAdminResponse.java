package com.blindtest.model.dto;

import com.blindtest.model.entity.CoverSource;
import com.blindtest.model.entity.TrackCovers;
import com.blindtest.model.entity.TrackEntity;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Immutable administrator metadata snapshot; storage object keys remain server-internal.
 *
 * @param title canonical primary answer: film title for film tracks, music title otherwise
 * @param coverUrl same-origin path of the stored cover, or {@code null}
 */
public record TrackAdminResponse(
        UUID id,
        String title,
        String artist,
        String album,
        String composer,
        String work,
        String category,
        Short decade,
        Integer durationSeconds,
        int excerptStartSeconds,
        int excerptDurationSeconds,
        String imageUrl,
        String coverUrl,
        CoverSource coverSource,
        Short difficulty,
        TrackStreamingLinks streamingLinks,
        String audioUrl,
        String audioChecksum,
        Long audioSizeBytes,
        String youtubeUrl,
        String status,
        LocalDateTime createdAt) {

    /** Creates an admin response DTO from the given entity, including all fields. */
    public static TrackAdminResponse from(TrackEntity entity) {
        return new TrackAdminResponse(
                entity.getId(), entity.getTitle(), entity.getArtist(),
                entity.getAlbum(), entity.getComposer(), entity.getWork(), entity.getCategory(),
                entity.getDecade(), entity.getDurationSeconds(), entity.getExcerptStartSeconds(),
                entity.getExcerptDurationSeconds(),
                entity.getImageUrl(),
                entity.getCoverId() == null ? null : TrackCovers.publicPath(entity.getCoverId()),
                entity.getCoverSource(), entity.getDifficulty(), TrackStreamingLinks.from(entity),
                entity.getAudioUrl(), entity.getAudioChecksum(), entity.getAudioSizeBytes(),
                entity.getYoutubeUrl(),
                entity.getStatus().name(), entity.getCreatedAt());
    }
}
