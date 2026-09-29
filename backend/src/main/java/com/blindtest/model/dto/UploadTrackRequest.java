package com.blindtest.model.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Optional metadata sent with {@code POST /api/admin/tracks/import/upload}. Blank values fall back
 * to the uploaded file's embedded tags.
 */
@Getter
@Setter
@NoArgsConstructor
public class UploadTrackRequest {

    @Size(max = 255)
    private String title;

    @Size(max = 255)
    private String artist;
}
