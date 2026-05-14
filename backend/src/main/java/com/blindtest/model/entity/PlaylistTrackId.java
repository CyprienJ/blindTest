package com.blindtest.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Composite primary key for {@link PlaylistTrackEntity}. */
@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PlaylistTrackId implements Serializable {

    @Column(name = "playlist_id")
    private UUID playlistId;

    @Column(name = "track_id")
    private UUID trackId;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PlaylistTrackId other)) {
            return false;
        }
        return Objects.equals(playlistId, other.playlistId)
            && Objects.equals(trackId, other.trackId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(playlistId, trackId);
    }
}
