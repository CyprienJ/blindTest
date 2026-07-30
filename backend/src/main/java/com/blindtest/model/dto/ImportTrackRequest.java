package com.blindtest.model.dto;

import com.blindtest.model.validation.SupportedYouTubeUrl;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Request body for POST /api/admin/tracks/import. */
@Getter
@NoArgsConstructor
public class ImportTrackRequest {

    @NotBlank
    @Size(max = 500)
    @SupportedYouTubeUrl
    private String youtubeUrl;

    @NotBlank
    @Size(max = 255)
    private String title;

    @NotBlank
    @Size(max = 255)
    private String artist;

    @Size(max = 255)
    private String album;
    @Size(max = 255)
    private String composer;
    @Size(max = 255)
    private String work;
    @Size(max = 100)
    private String category;
    @Min(1900)
    @Max(2090)
    private Short decade;
    @Positive
    private Integer durationSeconds;
    @Size(max = 500)
    private String imageUrl;
}
