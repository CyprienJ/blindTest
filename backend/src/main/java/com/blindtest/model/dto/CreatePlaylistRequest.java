package com.blindtest.model.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Request DTO for creating a new playlist. */
@Getter
@NoArgsConstructor
public class CreatePlaylistRequest {

    @NotBlank
    private String name;
    private String description;
    /** Uploaded cover candidate to store as the playlist cover. */
    private UUID coverCandidateId;
}
