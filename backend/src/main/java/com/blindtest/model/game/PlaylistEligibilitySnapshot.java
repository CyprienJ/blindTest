package com.blindtest.model.game;

import java.util.Map;
import java.util.UUID;

/** Immutable catalogue boundary projection used to validate one playlist selection. */
public record PlaylistEligibilitySnapshot(
        UUID playlistId,
        int eligiblePublishedTrackCount,
        Map<Integer, Integer> eligibleTrackCountsByDifficulty) {

    /** Key of {@link #eligibleTrackCountsByDifficulty} counting tracks without a difficulty. */
    public static final int UNRATED = 0;

    /** Projection without a per-difficulty breakdown; only an all-inclusive filter can match. */
    public PlaylistEligibilitySnapshot(UUID playlistId, int eligiblePublishedTrackCount) {
        this(playlistId, eligiblePublishedTrackCount, Map.of());
    }

    /** Rejects corrupt catalogue projections before they reach configuration validation. */
    public PlaylistEligibilitySnapshot {
        if (playlistId == null) {
            throw new IllegalArgumentException("playlistId is required");
        }
        if (eligiblePublishedTrackCount < 0) {
            throw new IllegalArgumentException("eligiblePublishedTrackCount cannot be negative");
        }
        eligibleTrackCountsByDifficulty = Map.copyOf(eligibleTrackCountsByDifficulty);
    }

    /** Counts eligible tracks kept by the given difficulty filter. */
    public int matchingTrackCount(DifficultyFilter filter) {
        if (filter.includesEverything()) {
            return eligiblePublishedTrackCount;
        }
        int matching = filter.includeUnrated()
                ? eligibleTrackCountsByDifficulty.getOrDefault(UNRATED, 0) : 0;
        for (int level : filter.levels()) {
            matching += eligibleTrackCountsByDifficulty.getOrDefault(level, 0);
        }
        return matching;
    }
}
