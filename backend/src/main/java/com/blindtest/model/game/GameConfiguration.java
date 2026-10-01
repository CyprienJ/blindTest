package com.blindtest.model.game;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Validated immutable game configuration snapshot.
 *
 * <p>It stores no playlist entity or live catalogue collection, so later playlist edits cannot
 * alter an active game's requested count or maximum round duration.
 */
public final class GameConfiguration {

    private final UUID playlistId;
    private final int requestedTrackCount;
    private final int maximumRoundDurationSeconds;
    private final Map<String, String> extensions;
    private final DifficultyFilter difficultyFilter;

    GameConfiguration(
            UUID playlistId,
            int requestedTrackCount,
            int maximumRoundDurationSeconds) {
        this(playlistId, requestedTrackCount, maximumRoundDurationSeconds, DifficultyFilter.ALL);
    }

    GameConfiguration(
            UUID playlistId,
            int requestedTrackCount,
            int maximumRoundDurationSeconds,
            DifficultyFilter difficultyFilter) {
        this.playlistId = playlistId;
        this.requestedTrackCount = requestedTrackCount;
        this.maximumRoundDurationSeconds = maximumRoundDurationSeconds;
        this.extensions = Map.of();
        this.difficultyFilter = difficultyFilter;
    }

    public UUID playlistId() {
        return playlistId;
    }

    public int requestedTrackCount() {
        return requestedTrackCount;
    }

    public int maximumRoundDurationSeconds() {
        return maximumRoundDurationSeconds;
    }

    public Map<String, String> extensions() {
        return extensions;
    }

    public DifficultyFilter difficultyFilter() {
        return difficultyFilter;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GameConfiguration that)) {
            return false;
        }
        return requestedTrackCount == that.requestedTrackCount
                && maximumRoundDurationSeconds == that.maximumRoundDurationSeconds
                && playlistId.equals(that.playlistId)
                && extensions.equals(that.extensions)
                && difficultyFilter.equals(that.difficultyFilter);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                playlistId, requestedTrackCount, maximumRoundDurationSeconds, extensions,
                difficultyFilter);
    }

    @Override
    public String toString() {
        return "GameConfiguration[playlistId=" + playlistId
                + ", requestedTrackCount=" + requestedTrackCount
                + ", maximumRoundDurationSeconds=" + maximumRoundDurationSeconds
                + ", extensions=" + extensions
                + ", difficultyFilter=" + difficultyFilter + ']';
    }
}
