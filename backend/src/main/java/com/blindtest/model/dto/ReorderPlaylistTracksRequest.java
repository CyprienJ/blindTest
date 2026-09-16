package com.blindtest.model.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Complete ordered track ID snapshot used for an atomic playlist reorder. */
@Getter
@NoArgsConstructor
public class ReorderPlaylistTracksRequest {

    @NotNull
    @Size(max = 500)
    private List<@NotNull UUID> trackIds;
}
