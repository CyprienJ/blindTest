package com.blindtest.model.game;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** Immutable, non-secret timing metadata for one active round's media excerpt. */
public record RoundPlaybackWindow(
        Instant startsAt,
        Instant deadline,
        int excerptStartSeconds,
        int excerptEndSeconds,
        Instant minimumUrlExpiresAt) {

    /** Bounded grace period during which an authorized reconnect can reuse a signed URL. */
    public static final Duration RECONNECT_BUFFER = Duration.ofSeconds(30);

    /** Rejects inconsistent timing, excerpt, and URL-validity boundaries. */
    public RoundPlaybackWindow {
        Objects.requireNonNull(startsAt, "startsAt is required");
        Objects.requireNonNull(deadline, "deadline is required");
        Objects.requireNonNull(minimumUrlExpiresAt, "minimumUrlExpiresAt is required");
        if (!startsAt.isBefore(deadline)) {
            throw new IllegalArgumentException("startsAt must precede deadline");
        }
        if (excerptStartSeconds < 0 || excerptEndSeconds <= excerptStartSeconds) {
            throw new IllegalArgumentException("excerpt bounds are invalid");
        }
        if (!minimumUrlExpiresAt.equals(deadline.plus(RECONNECT_BUFFER))) {
            throw new IllegalArgumentException("URL expiry boundary is invalid");
        }
        Duration roundDuration = Duration.between(startsAt, deadline);
        long roundSeconds = roundDuration.toSeconds();
        if (!Duration.ofSeconds(roundSeconds).equals(roundDuration)) {
            throw new IllegalArgumentException("round duration must use whole seconds");
        }
        if (roundSeconds != (long) excerptEndSeconds - excerptStartSeconds) {
            throw new IllegalArgumentException("excerpt duration must match round duration");
        }
    }

    /** Derives an active playback window from snapshotted catalogue and round timing. */
    public static RoundPlaybackWindow from(
            GamePlaybackSnapshot playback,
            RoundScoringWindow scoringWindow) {
        Objects.requireNonNull(playback, "playback is required");
        Objects.requireNonNull(scoringWindow, "scoringWindow is required");
        long durationSeconds = scoringWindow.configuredDuration().toSeconds();
        if (!Duration.ofSeconds(durationSeconds).equals(scoringWindow.configuredDuration())) {
            throw new IllegalArgumentException("round duration must use whole seconds");
        }
        int excerptEndSeconds = Math.toIntExact(
                Math.addExact((long) playback.excerptStartSeconds(), durationSeconds));
        return new RoundPlaybackWindow(
                scoringWindow.startsAt(),
                scoringWindow.deadline(),
                playback.excerptStartSeconds(),
                excerptEndSeconds,
                scoringWindow.deadline().plus(RECONNECT_BUFFER));
    }

    /** Returns whether a generated URL remains valid through the reconnect boundary. */
    public boolean isCoveredByUrlExpiringAt(Instant urlExpiresAt) {
        Objects.requireNonNull(urlExpiresAt, "urlExpiresAt is required");
        return !urlExpiresAt.isBefore(minimumUrlExpiresAt);
    }
}
