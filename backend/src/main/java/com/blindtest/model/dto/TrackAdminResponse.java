package com.blindtest.model.dto;

import com.blindtest.model.entity.TrackEntity;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** Admin track response DTO; storage object keys remain server-internal. */
@Getter
@AllArgsConstructor
public class TrackAdminResponse {

    private UUID id;
    private String title;
    private String artist;
    private String composer;
    private String work;
    private String category;
    private Short decade;
    private Integer durationSeconds;
    private String imageUrl;
    private String audioUrl;
    private String audioChecksum;
    private Long audioSizeBytes;
    private String youtubeUrl;
    private String status;
    private LocalDateTime createdAt;

    /** Creates an admin response DTO from the given entity, including all fields. */
    public static TrackAdminResponse from(TrackEntity entity) {
        return new TrackAdminResponse(
                entity.getId(), entity.getTitle(), entity.getArtist(),
                entity.getComposer(), entity.getWork(), entity.getCategory(),
                entity.getDecade(), entity.getDurationSeconds(), entity.getImageUrl(),
                entity.getAudioUrl(), entity.getAudioChecksum(), entity.getAudioSizeBytes(),
                entity.getYoutubeUrl(),
                entity.getStatus().name(), entity.getCreatedAt());
    }
}
