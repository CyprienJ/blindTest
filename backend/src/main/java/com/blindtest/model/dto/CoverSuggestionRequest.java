package com.blindtest.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Track metadata used to search cover suggestions; the album is optional. */
public record CoverSuggestionRequest(
        @NotBlank @Size(max = 255) String title,
        @NotBlank @Size(max = 255) String artist,
        @Size(max = 255) String album) {
}
