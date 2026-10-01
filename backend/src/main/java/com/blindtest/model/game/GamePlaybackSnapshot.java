package com.blindtest.model.game;

/**
 * Immutable catalogue playback metadata used by later round-window rules.
 *
 * @param excerptDurationSeconds length of the excerpt window, looped by longer rounds
 */
public record GamePlaybackSnapshot(
        int excerptStartSeconds,
        Integer mediaDurationSeconds,
        int excerptDurationSeconds) {

    /** Longest excerpt window, matching the longest approved round duration. */
    public static final int MAXIMUM_EXCERPT_DURATION_SECONDS = 60;

    /** Creates a snapshot with the full sixty-second window. */
    public GamePlaybackSnapshot(int excerptStartSeconds, Integer mediaDurationSeconds) {
        this(excerptStartSeconds, mediaDurationSeconds, MAXIMUM_EXCERPT_DURATION_SECONDS);
    }

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
        if (excerptDurationSeconds <= 0
                || excerptDurationSeconds > MAXIMUM_EXCERPT_DURATION_SECONDS) {
            throw new IllegalArgumentException("excerptDurationSeconds is out of range");
        }
    }
}
