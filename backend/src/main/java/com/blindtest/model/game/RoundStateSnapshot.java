package com.blindtest.model.game;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Immutable state snapshot for one selected game track. */
public record RoundStateSnapshot(
        GameTrackSnapshot track,
        RoundStatus status,
        Optional<RoundScoringWindow> scoringWindow,
        Optional<RoundOutcome> outcome,
        RoundScoreState scoreState,
        Optional<Instant> revealedAt,
        Optional<Instant> completedAt) {

    /** Validates the data required and forbidden in each lifecycle state. */
    public RoundStateSnapshot {
        Objects.requireNonNull(track, "track is required");
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(scoringWindow, "scoringWindow is required");
        Objects.requireNonNull(outcome, "outcome is required");
        Objects.requireNonNull(scoreState, "scoreState is required");
        Objects.requireNonNull(revealedAt, "revealedAt is required");
        Objects.requireNonNull(completedAt, "completedAt is required");
        validateShape(status, scoringWindow, outcome, scoreState, revealedAt, completedAt);
    }

    /** Creates the initial round state before authoritative playback starts. */
    public static RoundStateSnapshot preparing(GameTrackSnapshot track) {
        return new RoundStateSnapshot(
                track,
                RoundStatus.PREPARING,
                Optional.empty(),
                Optional.empty(),
                RoundScoreState.unscored(),
                Optional.empty(),
                Optional.empty());
    }

    private static void validateShape(
            RoundStatus status,
            Optional<RoundScoringWindow> scoringWindow,
            Optional<RoundOutcome> outcome,
            RoundScoreState scoreState,
            Optional<Instant> revealedAt,
            Optional<Instant> completedAt) {
        switch (status) {
            case PREPARING -> validatePreparing(
                    scoringWindow, outcome, scoreState, revealedAt, completedAt);
            case PLAYING -> validatePlaying(
                    scoringWindow, outcome, scoreState, revealedAt, completedAt);
            case REVEALED -> validateRevealed(
                    scoringWindow, outcome, revealedAt, completedAt);
            case COMPLETED -> validateCompleted(
                    scoringWindow, outcome, revealedAt, completedAt);
            default -> throw new IllegalStateException("Unknown round status");
        }
        if (outcome.isPresent()
                && (outcome.get() == RoundOutcome.TITLE_FOUND) != scoreState.titleAwarded()) {
            throw new IllegalArgumentException("title outcome and points must agree");
        }
        if (completedAt.isPresent() && completedAt.get().isBefore(revealedAt.orElseThrow())) {
            throw new IllegalArgumentException("completedAt cannot precede revealedAt");
        }
    }

    private static void validatePreparing(
            Optional<RoundScoringWindow> scoringWindow,
            Optional<RoundOutcome> outcome,
            RoundScoreState scoreState,
            Optional<Instant> revealedAt,
            Optional<Instant> completedAt) {
        require(scoringWindow.isEmpty() && outcome.isEmpty() && !scoreState.titleAwarded());
        require(revealedAt.isEmpty() && completedAt.isEmpty());
    }

    private static void validatePlaying(
            Optional<RoundScoringWindow> scoringWindow,
            Optional<RoundOutcome> outcome,
            RoundScoreState scoreState,
            Optional<Instant> revealedAt,
            Optional<Instant> completedAt) {
        require(scoringWindow.isPresent() && outcome.isEmpty() && !scoreState.titleAwarded());
        require(revealedAt.isEmpty() && completedAt.isEmpty());
    }

    private static void validateRevealed(
            Optional<RoundScoringWindow> scoringWindow,
            Optional<RoundOutcome> outcome,
            Optional<Instant> revealedAt,
            Optional<Instant> completedAt) {
        require(scoringWindow.isPresent() && outcome.isPresent());
        require(revealedAt.isPresent() && completedAt.isEmpty());
    }

    private static void validateCompleted(
            Optional<RoundScoringWindow> scoringWindow,
            Optional<RoundOutcome> outcome,
            Optional<Instant> revealedAt,
            Optional<Instant> completedAt) {
        require(scoringWindow.isPresent() && outcome.isPresent());
        require(revealedAt.isPresent() && completedAt.isPresent());
    }

    private static void require(boolean valid) {
        if (!valid) {
            throw new IllegalArgumentException("round snapshot does not match its status");
        }
    }
}
