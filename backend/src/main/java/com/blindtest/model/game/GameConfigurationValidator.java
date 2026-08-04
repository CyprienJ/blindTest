package com.blindtest.model.game;

/** Pure domain validator for game configuration and playlist eligibility. */
public final class GameConfigurationValidator {

    /**
     * Validates structural rules and an immutable catalogue eligibility projection.
     *
     * <p>Exact product limits and increments are deliberately added by GAME-02.
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
        if (requested.requestedTrackCount() <= 0) {
            throw failure(GameConfigurationError.TRACK_COUNT_INVALID);
        }
        if (requested.maximumRoundDurationSeconds() <= 0) {
            throw failure(GameConfigurationError.ROUND_DURATION_INVALID);
        }
        if (!requested.extensions().isEmpty()) {
            throw failure(GameConfigurationError.EXTENSION_UNSUPPORTED);
        }
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
        return new GameConfiguration(
                requested.playlistId(),
                requested.requestedTrackCount(),
                requested.maximumRoundDurationSeconds());
    }

    private GameConfigurationValidationException failure(GameConfigurationError error) {
        return new GameConfigurationValidationException(error);
    }
}
