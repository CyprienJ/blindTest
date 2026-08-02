package com.blindtest.model.dto;

import com.blindtest.model.validation.SupportedYouTubeUrl;
import jakarta.validation.constraints.NotBlank;
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

}
