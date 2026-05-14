package com.blindtest.model.dto;

import com.blindtest.model.entity.TrackEntity;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** Public track response DTO — excludes sensitive fields (audioUrl, youtubeUrl). */
@Getter
@AllArgsConstructor
public class TrackResponse {

    private UUID id;
    private String title;
    private String artist;
    private String composer;
    private String work;
    private String category;
    private Short decade;
    private Integer durationSeconds;
    private String imageUrl;

    /** Creates a public response DTO from the given entity, excluding sensitive fields. */
    public static TrackResponse from(TrackEntity entity) {
        return new TrackResponse(
                entity.getId(),
                entity.getTitle(),
                entity.getArtist(),
                entity.getComposer(),
                entity.getWork(),
                entity.getCategory(),
                entity.getDecade(),
                entity.getDurationSeconds(),
                entity.getImageUrl());
    }
}
