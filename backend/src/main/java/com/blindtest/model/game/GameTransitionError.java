package com.blindtest.model.game;

/** Stable failures produced by illegal game and round transitions. */
public enum GameTransitionError {
    ILLEGAL_GAME_TRANSITION(
            "GAME_ILLEGAL_TRANSITION", "The game transition is not allowed"),
    ILLEGAL_ROUND_TRANSITION(
            "GAME_ROUND_ILLEGAL_TRANSITION", "The round transition is not allowed"),
    TITLE_MATCH_REQUIRED(
            "GAME_TITLE_MATCH_REQUIRED", "A correct title match is required"),
    ROUND_DEADLINE_NOT_REACHED(
            "GAME_ROUND_DEADLINE_NOT_REACHED", "The round deadline has not been reached"),
    ROUND_TIME_INVALID(
            "GAME_ROUND_TIME_INVALID", "The round transition timestamp is invalid");

    private final String code;
    private final String safeMessage;

    GameTransitionError(String code, String safeMessage) {
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
