package com.blindtest.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Request body for POST /api/admin/tracks/import. */
@Getter
@NoArgsConstructor
public class ImportTrackRequest {

    @NotBlank
    private String youtubeUrl;

    @NotBlank
    private String title;

    @NotBlank
    private String artist;

    private String composer;
    private String work;
    private String category;
    private Short decade;
    private String imageUrl;
}
