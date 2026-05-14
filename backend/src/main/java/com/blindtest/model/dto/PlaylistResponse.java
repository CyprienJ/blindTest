package com.blindtest.model.dto;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** Public playlist summary response DTO. */
@Getter
@AllArgsConstructor
public class PlaylistResponse {

    private UUID id;
    private String name;
    private String description;
    private String imageUrl;
    private int trackCount;
}
