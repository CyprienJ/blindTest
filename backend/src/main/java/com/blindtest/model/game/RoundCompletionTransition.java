package com.blindtest.model.game;

import java.util.Objects;

/** Updated game snapshot and the one progression event caused by round completion. */
public record RoundCompletionTransition(
        GameStateSnapshot game,
        GameProgression progression) {

    /** Requires both immutable transition values. */
    public RoundCompletionTransition {
        Objects.requireNonNull(game, "game is required");
        Objects.requireNonNull(progression, "progression is required");
    }
}
