package com.blindtest.model.game;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Immutable aggregate snapshot for the complete solo game state machine. */
public record GameStateSnapshot(
        GameConfiguration configuration,
        GameTrackSelection trackSelection,
        GameStatus status,
        int currentRoundIndex,
        Optional<RoundStateSnapshot> currentRound,
        List<RoundStateSnapshot> completedRounds,
        Optional<GameSummary> finalSummary) {

    /** Defensively copies collections and verifies aggregate lifecycle invariants. */
    public GameStateSnapshot {
        Objects.requireNonNull(configuration, "configuration is required");
        Objects.requireNonNull(trackSelection, "trackSelection is required");
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(currentRound, "currentRound is required");
        Objects.requireNonNull(completedRounds, "completedRounds are required");
        Objects.requireNonNull(finalSummary, "finalSummary is required");
        completedRounds = List.copyOf(completedRounds);
        validateSelection(configuration, trackSelection);
        validateShape(
                trackSelection, status, currentRoundIndex,
                currentRound, completedRounds, finalSummary);
    }

    /** Creates a game before its first round is prepared. */
    public static GameStateSnapshot created(
            GameConfiguration configuration,
            GameTrackSelection trackSelection) {
        return new GameStateSnapshot(
                configuration,
                trackSelection,
                GameStatus.CREATED,
                -1,
                Optional.empty(),
                List.of(),
                Optional.empty());
    }

    /** Returns the score accumulated by completed rounds. */
    public int totalScore() {
        return completedRounds.stream().
                mapToInt(round -> round.scoreState().totalPoints()).
                sum();
    }

    private static void validateSelection(
            GameConfiguration configuration,
            GameTrackSelection trackSelection) {
        if (trackSelection.tracks().isEmpty()
                || trackSelection.tracks().size() != configuration.requestedTrackCount()) {
            throw new IllegalArgumentException("track selection must match configured count");
        }
    }

    private static void validateShape(
            GameTrackSelection selection,
            GameStatus status,
            int currentRoundIndex,
            Optional<RoundStateSnapshot> currentRound,
            List<RoundStateSnapshot> completedRounds,
            Optional<GameSummary> finalSummary) {
        requireCompletedRoundsOrdered(completedRounds);
        switch (status) {
            case CREATED -> require(currentRoundIndex == -1 && currentRound.isEmpty()
                    && completedRounds.isEmpty() && finalSummary.isEmpty());
            case PLAYING -> validatePlaying(
                    selection, currentRoundIndex, currentRound, completedRounds, finalSummary);
            case FINISHED -> require(currentRoundIndex == selection.tracks().size()
                    && currentRound.isEmpty()
                    && completedRounds.size() == selection.tracks().size()
                    && finalSummary.filter(GameSummary.from(completedRounds)::equals).isPresent());
            case ABORTED -> validateAborted(
                    selection, currentRoundIndex, currentRound, completedRounds, finalSummary);
            default -> throw new IllegalStateException("Unknown game status");
        }
    }

    private static void validatePlaying(
            GameTrackSelection selection,
            int currentRoundIndex,
            Optional<RoundStateSnapshot> currentRound,
            List<RoundStateSnapshot> completedRounds,
            Optional<GameSummary> finalSummary) {
        require(currentRoundIndex >= 0 && currentRoundIndex < selection.tracks().size()
                && currentRound.isPresent()
                && completedRounds.size() == currentRoundIndex
                && finalSummary.isEmpty());
        require(currentRound.orElseThrow().track().equals(
                selection.tracks().get(currentRoundIndex)));
    }

    private static void validateAborted(
            GameTrackSelection selection,
            int currentRoundIndex,
            Optional<RoundStateSnapshot> currentRound,
            List<RoundStateSnapshot> completedRounds,
            Optional<GameSummary> finalSummary) {
        require(finalSummary.isEmpty()
                && currentRoundIndex >= -1
                && currentRoundIndex < selection.tracks().size());
        if (currentRoundIndex == -1) {
            require(currentRound.isEmpty() && completedRounds.isEmpty());
        } else {
            require(currentRound.isPresent()
                    && completedRounds.size() == currentRoundIndex
                    && currentRound.orElseThrow().track().equals(
                            selection.tracks().get(currentRoundIndex)));
        }
    }

    private static void requireCompletedRoundsOrdered(List<RoundStateSnapshot> completedRounds) {
        for (int index = 0; index < completedRounds.size(); index++) {
            RoundStateSnapshot round = completedRounds.get(index);
            require(round.status() == RoundStatus.COMPLETED
                    && round.track().playOrder() == index);
        }
    }

    private static void require(boolean valid) {
        if (!valid) {
            throw new IllegalArgumentException("game snapshot does not match its status");
        }
    }
}
