package com.blindtest.model.game;

/** Lifecycle state of an active game aggregate. */
public enum GameStatus {
    CREATED,
    PLAYING,
    FINISHED,
    ABORTED
}
