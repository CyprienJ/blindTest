package com.blindtest.model.game;

/** Stable domain failures raised while validating a requested game configuration. */
public enum GameConfigurationError {

    CONFIGURATION_REQUIRED(
            "GAME_CONFIGURATION_REQUIRED", "A game configuration is required"),
    PLAYLIST_REQUIRED(
            "GAME_PLAYLIST_REQUIRED", "A playlist must be selected"),
    TRACK_COUNT_INVALID(
            "GAME_TRACK_COUNT_INVALID", "The requested track count must be positive"),
    ROUND_DURATION_INVALID(
            "GAME_ROUND_DURATION_INVALID", "The maximum round duration must be positive"),
    EXTENSION_UNSUPPORTED(
            "GAME_CONFIGURATION_EXTENSION_UNSUPPORTED",
            "The requested game configuration option is not supported"),
    PLAYLIST_NOT_FOUND(
            "GAME_PLAYLIST_NOT_FOUND", "The selected playlist is unavailable"),
    PLAYLIST_INELIGIBLE(
            "GAME_PLAYLIST_INELIGIBLE", "The selected playlist has no eligible tracks"),
    PLAYLIST_INSUFFICIENT_TRACKS(
            "GAME_PLAYLIST_INSUFFICIENT_TRACKS",
            "The selected playlist does not contain enough eligible tracks");

    private final String code;
    private final String safeMessage;

    GameConfigurationError(String code, String safeMessage) {
        this.code = code;
        this.safeMessage = safeMessage;
    }

    public String code() {
        return code;
    }

    public String safeMessage() {
        return safeMessage;
    }
}
