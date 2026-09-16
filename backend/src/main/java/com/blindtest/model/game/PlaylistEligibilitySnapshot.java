package com.blindtest.model.game;

import java.util.UUID;

/** Immutable catalogue boundary projection used to validate one playlist selection. */
public record PlaylistEligibilitySnapshot(UUID playlistId, int eligiblePublishedTrackCount) {

    /** Rejects corrupt catalogue projections before they reach configuration validation. */
    public PlaylistEligibilitySnapshot {
        if (playlistId == null) {
            throw new IllegalArgumentException("playlistId is required");
        }
        if (eligiblePublishedTrackCount < 0) {
            throw new IllegalArgumentException("eligiblePublishedTrackCount cannot be negative");
        }
    }
}
