package com.blindtest.model.game;

import java.util.HashSet;
import java.util.List;

/** Pure domain validator for game configuration and playlist eligibility. */
public final class GameConfigurationValidator {

    /**
     * Validates authoritative product limits and an immutable catalogue eligibility projection.
     */
    public GameConfiguration validate(
            RequestedGameConfiguration requested,
            PlaylistEligibilitySnapshot playlistEligibility) {
        if (requested == null) {
            throw failure(GameConfigurationError.CONFIGURATION_REQUIRED);
        }
        if (requested.playlistId() == null) {
            throw failure(GameConfigurationError.PLAYLIST_REQUIRED);
        }
        if (!GameConfigurationLimits.isAllowedTrackCount(requested.requestedTrackCount())) {
            throw failure(GameConfigurationError.TRACK_COUNT_INVALID);
        }
        if (!GameConfigurationLimits.isAllowedRoundDuration(
                requested.maximumRoundDurationSeconds())) {
            throw failure(GameConfigurationError.ROUND_DURATION_INVALID);
        }
        if (!requested.extensions().isEmpty()) {
            throw failure(GameConfigurationError.EXTENSION_UNSUPPORTED);
        }
        DifficultyFilter difficultyFilter = difficultyFilter(requested);
        if (playlistEligibility == null
                || !requested.playlistId().equals(playlistEligibility.playlistId())) {
            throw failure(GameConfigurationError.PLAYLIST_NOT_FOUND);
        }
        if (playlistEligibility.eligiblePublishedTrackCount() == 0) {
            throw failure(GameConfigurationError.PLAYLIST_INELIGIBLE);
        }
        if (playlistEligibility.eligiblePublishedTrackCount()
                < requested.requestedTrackCount()) {
            throw failure(GameConfigurationError.PLAYLIST_INSUFFICIENT_TRACKS);
        }
        if (playlistEligibility.matchingTrackCount(difficultyFilter)
                < requested.requestedTrackCount()) {
            throw failure(GameConfigurationError.DIFFICULTY_INSUFFICIENT_TRACKS);
        }
        return new GameConfiguration(
                requested.playlistId(),
                requested.requestedTrackCount(),
                requested.maximumRoundDurationSeconds(),
                difficultyFilter);
    }

    private DifficultyFilter difficultyFilter(RequestedGameConfiguration requested) {
        List<Integer> levels = requested.difficultyLevels();
        if (levels == null) {
            return requested.includeUnrated()
                    ? DifficultyFilter.ALL
                    : new DifficultyFilter(DifficultyFilter.ALL.levels(), false);
        }
        if (!levels.stream().allMatch(DifficultyFilter::isAllowedLevel)
                || levels.isEmpty() && !requested.includeUnrated()) {
            throw failure(GameConfigurationError.DIFFICULTY_INVALID);
        }
        return new DifficultyFilter(new HashSet<>(levels), requested.includeUnrated());
    }

    private GameConfigurationValidationException failure(GameConfigurationError error) {
        return new GameConfigurationValidationException(error);
    }
}
