package com.blindtest.model.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

/** Request DTO for partially updating playlist metadata; all fields are optional. */
@Getter
@NoArgsConstructor
public class UpdatePlaylistRequest {

    private String name;
    private String description;
    private String imageUrl;
}
