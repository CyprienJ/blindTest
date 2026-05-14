package com.blindtest.model.dto;

import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** Public playlist detail response DTO — includes the ordered list of published tracks. */
@Getter
@AllArgsConstructor
public class PlaylistDetailResponse {

    private UUID id;
    private String name;
    private String description;
    private String imageUrl;
    private int trackCount;
    private List<TrackResponse> tracks;
}
