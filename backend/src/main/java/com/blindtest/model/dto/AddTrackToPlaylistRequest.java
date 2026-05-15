package com.blindtest.model.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Request DTO for adding a track to a playlist with an explicit position. */
@Getter
@NoArgsConstructor
public class AddTrackToPlaylistRequest {

    @NotNull
    private UUID trackId;
    private int order;
}
