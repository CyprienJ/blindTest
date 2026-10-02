package com.blindtest.model.game;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Immutable aggregate snapshot for the game state machine, played by one participant (solo)
 * or up to {@link #MAXIMUM_PARTICIPANTS} participants listed in join order.
 */
public record GameStateSnapshot(
        GameConfiguration configuration,
        GameTrackSelection trackSelection,
        List<GameParticipantId> participants,
        GameStatus status,
        int currentRoundIndex,
        Optional<RoundStateSnapshot> currentRound,
        List<RoundStateSnapshot> completedRounds,
        Optional<GameSummary> finalSummary) {

    /** Lobby cap from ADR-0019, host included. */
    public static final int MAXIMUM_PARTICIPANTS = 10;

    /** Defensively copies collections and verifies aggregate lifecycle invariants. */
    public GameStateSnapshot {
        Objects.requireNonNull(configuration, "configuration is required");
        Objects.requireNonNull(trackSelection, "trackSelection is required");
        Objects.requireNonNull(participants, "participants are required");
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(currentRound, "currentRound is required");
        Objects.requireNonNull(completedRounds, "completedRounds are required");
        Objects.requireNonNull(finalSummary, "finalSummary is required");
        participants = List.copyOf(participants);
        completedRounds = List.copyOf(completedRounds);
        validateSelection(configuration, trackSelection);
        validateParticipants(participants, currentRound, completedRounds);
        validateShape(
                trackSelection, participants, status, currentRoundIndex,
                currentRound, completedRounds, finalSummary);
    }

    /** Creates a game before its first round is prepared. */
    public static GameStateSnapshot created(
            GameConfiguration configuration,
            GameTrackSelection trackSelection,
            List<GameParticipantId> participants) {
        return new GameStateSnapshot(
                configuration,
                trackSelection,
                participants,
                GameStatus.CREATED,
                -1,
                Optional.empty(),
                List.of(),
                Optional.empty());
    }

    /** Returns the score accumulated by completed rounds for every participant. */
    public int totalScore() {
        return completedRounds.stream().
                mapToInt(round -> round.scoreState().totalPoints()).
                sum();
    }

    /** Returns one participant's score from completed rounds and the current round. */
    public int totalScoreFor(GameParticipantId participantId) {
        return Stream.concat(completedRounds.stream(), currentRound.stream()).
                mapToInt(round -> round.scoreState().pointsFor(participantId)).
                sum();
    }

    /** Returns whether the participant belongs to this game. */
    public boolean hasParticipant(GameParticipantId participantId) {
        return participants.contains(participantId);
    }

    private static void validateParticipants(
            List<GameParticipantId> participants,
            Optional<RoundStateSnapshot> currentRound,
            List<RoundStateSnapshot> completedRounds) {
        if (participants.isEmpty() || participants.size() > MAXIMUM_PARTICIPANTS) {
            throw new IllegalArgumentException("a game needs one to ten participants");
        }
        Set<GameParticipantId> known = new HashSet<>(participants);
        if (known.size() != participants.size()) {
            throw new IllegalArgumentException("participant identities must be unique");
        }
        boolean foreignState = Stream.concat(completedRounds.stream(), currentRound.stream()).
                map(RoundStateSnapshot::scoreState).
                anyMatch(state -> !known.containsAll(state.titleAwards().keySet())
                        || !known.containsAll(state.skipVotes()));
        if (foreignState) {
            throw new IllegalArgumentException("round scores must belong to game participants");
        }
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
            List<GameParticipantId> participants,
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
                    && finalSummary.filter(GameSummary.from(participants, completedRounds)::equals).isPresent());
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
