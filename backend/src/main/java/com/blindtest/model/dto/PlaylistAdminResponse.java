package com.blindtest.model.dto;

import com.blindtest.model.entity.PlaylistEntity;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** Admin playlist summary DTO — includes all tracks regardless of status. */
@Getter
@AllArgsConstructor
public class PlaylistAdminResponse {

    private UUID id;
    private String name;
    private String description;
    private String imageUrl;
    private int trackCount;

    /** Creates a summary response from the given entity and total track count. */
    public static PlaylistAdminResponse from(PlaylistEntity entity, int trackCount) {
        return new PlaylistAdminResponse(entity.getId(), entity.getName(),
                entity.getDescription(), entity.getImageUrl(), trackCount);
    }
}
