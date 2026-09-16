package com.blindtest.model.game;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Immutable ordered result of selecting and snapshotting tracks for one game. */
public record GameTrackSelection(List<GameTrackSnapshot> tracks) {

    /** Defensively copies the ordered tracks and verifies their identity and order invariants. */
    public GameTrackSelection {
        Objects.requireNonNull(tracks, "tracks are required");
        tracks = List.copyOf(tracks);
        Set<UUID> identities = new HashSet<>();
        for (int index = 0; index < tracks.size(); index++) {
            GameTrackSnapshot track = tracks.get(index);
            if (track.playOrder() != index) {
                throw new IllegalArgumentException("track play orders must be contiguous");
            }
            if (!identities.add(track.trackId())) {
                throw new IllegalArgumentException("track identities must be unique");
            }
        }
    }
}
