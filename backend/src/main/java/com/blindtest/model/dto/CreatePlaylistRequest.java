package com.blindtest.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Request DTO for creating a new playlist. */
@Getter
@NoArgsConstructor
public class CreatePlaylistRequest {

    @NotBlank
    private String name;
    private String description;
    private String imageUrl;
}
