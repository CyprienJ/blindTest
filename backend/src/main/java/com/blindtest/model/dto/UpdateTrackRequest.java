package com.blindtest.model.dto;

import com.blindtest.model.entity.TrackPublicationPolicy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Request body for partial metadata update on a track. Null fields are left unchanged. */
@Getter
@NoArgsConstructor
public class UpdateTrackRequest {

    private String title;
    private String artist;
    private String album;
    private String composer;
    private String work;
    private String category;
    private Short decade;
    private Integer durationSeconds;
    @Min(0)
    private Integer excerptStartSeconds;
    @Min(TrackPublicationPolicy.MINIMUM_EXCERPT_DURATION_SECONDS)
    @Max(TrackPublicationPolicy.MAXIMUM_EXCERPT_DURATION_SECONDS)
    private Integer excerptDurationSeconds;
    private String imageUrl;
    @Min(1)
    @Max(5)
    private Short difficulty;
    /** When present, replaces all streaming links; blank links are cleared. */
    @Valid
    private TrackStreamingLinks streamingLinks;
    /** Cover suggestion or upload to store as the new cover; ignored when removing. */
    private UUID coverCandidateId;
    /** When {@code true}, clears the stored cover. */
    private Boolean removeCover;
}
