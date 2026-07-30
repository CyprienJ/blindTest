package com.blindtest.model.dto;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Request body for partial metadata update on a track. Null fields are left unchanged. */
@Getter
@NoArgsConstructor
public class UpdateTrackRequest {

    private String title;
    private String artist;
    private String album;
    private String composer;
    private String work;
    private String category;
    private Short decade;
    private Integer durationSeconds;
    @Min(0)
    private Integer excerptStartSeconds;
    private String imageUrl;
}
