package com.blindtest.model.game;

/** Authoritative game configuration limits from ADR-0008 and ADR-0024. */
public final class GameConfigurationLimits {

    public static final int MINIMUM_TRACK_COUNT = 5;
    public static final int MAXIMUM_TRACK_COUNT = 500;
    public static final int TRACK_COUNT_INCREMENT = 1;
    public static final int DEFAULT_TRACK_COUNT = 15;

    public static final int MINIMUM_ROUND_DURATION_SECONDS = 10;
    public static final int MAXIMUM_ROUND_DURATION_SECONDS = 60;
    public static final int ROUND_DURATION_INCREMENT_SECONDS = 5;
    public static final int DEFAULT_ROUND_DURATION_SECONDS = 30;

    private GameConfigurationLimits() {
    }

    /** Returns whether a track count is within the approved inclusive range. */
    public static boolean isAllowedTrackCount(int trackCount) {
        return trackCount >= MINIMUM_TRACK_COUNT && trackCount <= MAXIMUM_TRACK_COUNT;
    }

    /** Returns whether a round duration is in range and on an approved five-second step. */
    public static boolean isAllowedRoundDuration(int durationSeconds) {
        return durationSeconds >= MINIMUM_ROUND_DURATION_SECONDS
                && durationSeconds <= MAXIMUM_ROUND_DURATION_SECONDS
                && (durationSeconds - MINIMUM_ROUND_DURATION_SECONDS)
                % ROUND_DURATION_INCREMENT_SECONDS == 0;
    }
}
