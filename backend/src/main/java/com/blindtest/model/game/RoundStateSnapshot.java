package com.blindtest.model.game;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Immutable state snapshot for one selected game track. */
public record RoundStateSnapshot(
        GameTrackSnapshot track,
        RoundStatus status,
        Optional<RoundScoringWindow> scoringWindow,
        Optional<RoundPlaybackWindow> playbackWindow,
        Optional<RoundOutcome> outcome,
        RoundScoreState scoreState,
        Optional<Instant> revealedAt,
        Optional<Instant> completedAt) {

    /** Validates the data required and forbidden in each lifecycle state. */
    public RoundStateSnapshot {
        Objects.requireNonNull(track, "track is required");
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(scoringWindow, "scoringWindow is required");
        Objects.requireNonNull(playbackWindow, "playbackWindow is required");
        Objects.requireNonNull(outcome, "outcome is required");
        Objects.requireNonNull(scoreState, "scoreState is required");
        Objects.requireNonNull(revealedAt, "revealedAt is required");
        Objects.requireNonNull(completedAt, "completedAt is required");
        validateShape(
                track, status, scoringWindow, playbackWindow,
                outcome, scoreState, revealedAt, completedAt);
    }

    /** Creates the initial round state before authoritative playback starts. */
    public static RoundStateSnapshot preparing(GameTrackSnapshot track) {
        return new RoundStateSnapshot(
                track,
                RoundStatus.PREPARING,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                RoundScoreState.unscored(),
                Optional.empty(),
                Optional.empty());
    }

    private static void validateShape(
            GameTrackSnapshot track,
            RoundStatus status,
            Optional<RoundScoringWindow> scoringWindow,
            Optional<RoundPlaybackWindow> playbackWindow,
            Optional<RoundOutcome> outcome,
            RoundScoreState scoreState,
            Optional<Instant> revealedAt,
            Optional<Instant> completedAt) {
        switch (status) {
            case PREPARING -> validatePreparing(
                    scoringWindow, playbackWindow, outcome, scoreState, revealedAt, completedAt);
            case PLAYING -> validatePlaying(
                    scoringWindow, playbackWindow, outcome, revealedAt, completedAt);
            case REVEALED -> validateRevealed(
                    scoringWindow, playbackWindow, outcome, revealedAt, completedAt);
            case COMPLETED -> validateCompleted(
                    scoringWindow, playbackWindow, outcome, revealedAt, completedAt);
            default -> throw new IllegalStateException("Unknown round status");
        }
        // Several participants may find the title before a skip vote or timeout, so only a
        // title-found reveal strictly requires an award.
        if (outcome.filter(RoundOutcome.TITLE_FOUND::equals).isPresent()
                && !scoreState.titleAwarded()) {
            throw new IllegalArgumentException("title outcome and points must agree");
        }
        if (completedAt.isPresent() && completedAt.get().isBefore(revealedAt.orElseThrow())) {
            throw new IllegalArgumentException("completedAt cannot precede revealedAt");
        }
        if (playbackWindow.isPresent()) {
            RoundPlaybackWindow playback = playbackWindow.orElseThrow();
            RoundScoringWindow scoring = scoringWindow.orElseThrow();
            if (!playback.startsAt().equals(scoring.startsAt())
                    || !playback.deadline().equals(scoring.deadline())
                    || playback.excerptStartSeconds() != track.playback().excerptStartSeconds()) {
                throw new IllegalArgumentException("playback window does not match round snapshot");
            }
        }
    }

    private static void validatePreparing(
            Optional<RoundScoringWindow> scoringWindow,
            Optional<RoundPlaybackWindow> playbackWindow,
            Optional<RoundOutcome> outcome,
            RoundScoreState scoreState,
            Optional<Instant> revealedAt,
            Optional<Instant> completedAt) {
        require(scoringWindow.isEmpty() && playbackWindow.isEmpty()
                && outcome.isEmpty() && scoreState.equals(RoundScoreState.unscored()));
        require(revealedAt.isEmpty() && completedAt.isEmpty());
    }

    private static void validatePlaying(
            Optional<RoundScoringWindow> scoringWindow,
            Optional<RoundPlaybackWindow> playbackWindow,
            Optional<RoundOutcome> outcome,
            Optional<Instant> revealedAt,
            Optional<Instant> completedAt) {
        require(scoringWindow.isPresent() && playbackWindow.isPresent() && outcome.isEmpty());
        require(revealedAt.isEmpty() && completedAt.isEmpty());
    }

    private static void validateRevealed(
            Optional<RoundScoringWindow> scoringWindow,
            Optional<RoundPlaybackWindow> playbackWindow,
            Optional<RoundOutcome> outcome,
            Optional<Instant> revealedAt,
            Optional<Instant> completedAt) {
        require(scoringWindow.isPresent() && playbackWindow.isPresent() && outcome.isPresent());
        require(revealedAt.isPresent() && completedAt.isEmpty());
    }

    private static void validateCompleted(
            Optional<RoundScoringWindow> scoringWindow,
            Optional<RoundPlaybackWindow> playbackWindow,
            Optional<RoundOutcome> outcome,
            Optional<Instant> revealedAt,
            Optional<Instant> completedAt) {
        require(scoringWindow.isPresent() && playbackWindow.isPresent() && outcome.isPresent());
        require(revealedAt.isPresent() && completedAt.isPresent());
    }

    private static void require(boolean valid) {
        if (!valid) {
            throw new IllegalArgumentException("round snapshot does not match its status");
        }
    }
}
