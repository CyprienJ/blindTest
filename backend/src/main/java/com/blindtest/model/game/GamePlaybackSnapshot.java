package com.blindtest.model.game;

/** Immutable catalogue playback metadata used by later round-window rules. */
public record GamePlaybackSnapshot(int excerptStartSeconds, Integer mediaDurationSeconds) {

    /** Rejects impossible offsets and durations while allowing an unknown duration at offset zero. */
    public GamePlaybackSnapshot {
        if (excerptStartSeconds < 0) {
            throw new IllegalArgumentException("excerptStartSeconds cannot be negative");
        }
        if (mediaDurationSeconds != null && mediaDurationSeconds <= 0) {
            throw new IllegalArgumentException("mediaDurationSeconds must be positive");
        }
        if (mediaDurationSeconds != null && excerptStartSeconds >= mediaDurationSeconds) {
            throw new IllegalArgumentException("excerptStartSeconds must precede media duration");
        }
        if (excerptStartSeconds > 0 && mediaDurationSeconds == null) {
            throw new IllegalArgumentException("a non-zero excerpt requires media duration");
        }
    }
}
