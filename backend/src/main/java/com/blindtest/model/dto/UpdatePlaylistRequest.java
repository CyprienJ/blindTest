package com.blindtest.model.dto;

import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Request DTO for partially updating playlist metadata; all fields are optional. */
@Getter
@NoArgsConstructor
public class UpdatePlaylistRequest {

    private String name;
    private String description;
    /** Uploaded cover candidate to store as the new playlist cover; ignored when removing. */
    private UUID coverCandidateId;
    /** When {@code true}, clears the stored cover and any legacy image URL. */
    private Boolean removeCover;
}
