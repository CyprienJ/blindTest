package com.blindtest.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** JPA entity mapped to the {@code playlist_tracks} join table. */
@Entity
@Table(name = "playlist_tracks")
@Getter
@Setter
@NoArgsConstructor
public class PlaylistTrackEntity {

    @EmbeddedId
    private PlaylistTrackId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("playlistId")
    @JoinColumn(name = "playlist_id")
    private PlaylistEntity playlist;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("trackId")
    @JoinColumn(name = "track_id")
    private TrackEntity track;

    @Column(name = "\"order\"", nullable = false)
    private int trackOrder;

    /** Creates an association with an explicit position. */
    public PlaylistTrackEntity(PlaylistEntity playlist, TrackEntity track, int trackOrder) {
        this.id = new PlaylistTrackId(playlist.getId(), track.getId());
        this.playlist = playlist;
        this.track = track;
        this.trackOrder = trackOrder;
    }
}
