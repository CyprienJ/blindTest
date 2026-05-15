package com.blindtest.model.dto;

import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** Admin playlist detail DTO — includes all tracks (all statuses) ordered by position. */
@Getter
@AllArgsConstructor
public class PlaylistAdminDetailResponse {

    private UUID id;
    private String name;
    private String description;
    private String imageUrl;
    private int trackCount;
    private List<TrackAdminResponse> tracks;
}
