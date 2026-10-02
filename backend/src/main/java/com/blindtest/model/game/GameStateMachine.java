package com.blindtest.model.game;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Pure aggregate transitions for ordered rounds and terminal game states. */
public final class GameStateMachine {

    private final RoundStateMachine roundStateMachine;

    public GameStateMachine(RoundStateMachine roundStateMachine) {
        this.roundStateMachine = Objects.requireNonNull(
                roundStateMachine, "roundStateMachine is required");
    }

    /** Starts a created game by preparing its first selected track. */
    public GameStateSnapshot start(GameStateSnapshot game) {
        requireStatus(game, GameStatus.CREATED);
        RoundStateSnapshot firstRound =
                RoundStateSnapshot.preparing(game.trackSelection().tracks().getFirst());
        return copy(game, GameStatus.PLAYING, 0, Optional.of(firstRound), List.of(), Optional.empty());
    }

    /** Starts the active prepared round with an authoritative scoring window. */
    public GameStateSnapshot startRound(
            GameStateSnapshot game,
            RoundScoringWindow scoringWindow) {
        requireStatus(game, GameStatus.PLAYING);
        Objects.requireNonNull(scoringWindow, "scoringWindow is required");
        Duration configuredDuration =
                Duration.ofSeconds(game.configuration().maximumRoundDurationSeconds());
        if (!configuredDuration.equals(scoringWindow.configuredDuration())) {
            throw failure(GameTransitionError.ROUND_TIME_INVALID);
        }
        RoundStateSnapshot started = roundStateMachine.start(currentRound(game), scoringWindow);
        return withCurrentRound(game, started);
    }

    /** Awards one participant's correct title and reveals once every active one found it. */
    public GameStateSnapshot awardTitle(
            GameStateSnapshot game,
            GameParticipantId participantId,
            AnswerMatchResult match,
            Instant acceptedAt,
            Set<GameParticipantId> activeParticipants) {
        requireStatus(game, GameStatus.PLAYING);
        requireParticipants(game, participantId, activeParticipants);
        RoundStateSnapshot round = roundStateMachine.awardTitle(
                currentRound(game), participantId, match, acceptedAt, activeParticipants);
        return withCurrentRound(game, round);
    }

    /** Records one participant's skip vote and reveals once the vote passes. */
    public GameStateSnapshot voteSkip(
            GameStateSnapshot game,
            GameParticipantId participantId,
            Instant votedAt,
            Set<GameParticipantId> activeParticipants) {
        requireStatus(game, GameStatus.PLAYING);
        requireParticipants(game, participantId, activeParticipants);
        RoundStateSnapshot round = roundStateMachine.voteSkip(
                currentRound(game), participantId, votedAt, activeParticipants);
        return withCurrentRound(game, round);
    }

    /** Re-checks the playing round after the active participant set changed. */
    public GameStateSnapshot reevaluateRound(
            GameStateSnapshot game,
            Instant evaluatedAt,
            Set<GameParticipantId> activeParticipants) {
        requireStatus(game, GameStatus.PLAYING);
        Objects.requireNonNull(activeParticipants, "activeParticipants are required");
        if (!game.participants().containsAll(activeParticipants)) {
            throw failure(GameTransitionError.UNKNOWN_PARTICIPANT);
        }
        RoundStateSnapshot round = roundStateMachine.reevaluate(
                currentRound(game), evaluatedAt, activeParticipants);
        return withCurrentRound(game, round);
    }

    /** Resolves the active round as timed out. */
    public GameStateSnapshot timeoutRound(GameStateSnapshot game, Instant timedOutAt) {
        requireStatus(game, GameStatus.PLAYING);
        RoundStateSnapshot revealed = roundStateMachine.timeout(currentRound(game), timedOutAt);
        return withCurrentRound(game, revealed);
    }

    /**
     * Completes the active revealed round, then prepares the next track or finishes once.
     */
    public RoundCompletionTransition completeRound(
            GameStateSnapshot game,
            Instant completedAt) {
        requireStatus(game, GameStatus.PLAYING);
        RoundStateSnapshot completed =
                roundStateMachine.complete(currentRound(game), completedAt);
        List<RoundStateSnapshot> completedRounds = new ArrayList<>(game.completedRounds());
        completedRounds.add(completed);

        if (completedRounds.size() == game.trackSelection().tracks().size()) {
            GameSummary summary = GameSummary.from(game.participants(), completedRounds);
            GameStateSnapshot finished = copy(
                    game,
                    GameStatus.FINISHED,
                    completedRounds.size(),
                    Optional.empty(),
                    completedRounds,
                    Optional.of(summary));
            return new RoundCompletionTransition(finished, GameProgression.GAME_FINISHED);
        }

        int nextRoundIndex = completedRounds.size();
        RoundStateSnapshot nextRound = RoundStateSnapshot.preparing(
                game.trackSelection().tracks().get(nextRoundIndex));
        GameStateSnapshot progressed = copy(
                game,
                GameStatus.PLAYING,
                nextRoundIndex,
                Optional.of(nextRound),
                completedRounds,
                Optional.empty());
        return new RoundCompletionTransition(
                progressed, GameProgression.NEXT_ROUND_PREPARED);
    }

    /** Aborts a created or playing game without manufacturing a final summary. */
    public GameStateSnapshot abort(GameStateSnapshot game) {
        Objects.requireNonNull(game, "game is required");
        if (game.status() != GameStatus.CREATED && game.status() != GameStatus.PLAYING) {
            throw failure(GameTransitionError.ILLEGAL_GAME_TRANSITION);
        }
        return copy(
                game,
                GameStatus.ABORTED,
                game.currentRoundIndex(),
                game.currentRound(),
                game.completedRounds(),
                Optional.empty());
    }

    private GameStateSnapshot withCurrentRound(
            GameStateSnapshot game,
            RoundStateSnapshot currentRound) {
        return copy(
                game,
                game.status(),
                game.currentRoundIndex(),
                Optional.of(currentRound),
                game.completedRounds(),
                game.finalSummary());
    }

    private GameStateSnapshot copy(
            GameStateSnapshot game,
            GameStatus status,
            int currentRoundIndex,
            Optional<RoundStateSnapshot> currentRound,
            List<RoundStateSnapshot> completedRounds,
            Optional<GameSummary> finalSummary) {
        return new GameStateSnapshot(
                game.configuration(),
                game.trackSelection(),
                game.participants(),
                status,
                currentRoundIndex,
                currentRound,
                completedRounds,
                finalSummary);
    }

    private RoundStateSnapshot currentRound(GameStateSnapshot game) {
        return game.currentRound().orElseThrow(
                () -> failure(GameTransitionError.ILLEGAL_GAME_TRANSITION));
    }

    private void requireParticipants(
            GameStateSnapshot game,
            GameParticipantId participantId,
            Set<GameParticipantId> activeParticipants) {
        Objects.requireNonNull(participantId, "participantId is required");
        Objects.requireNonNull(activeParticipants, "activeParticipants are required");
        if (!game.hasParticipant(participantId)
                || !game.participants().containsAll(activeParticipants)) {
            throw failure(GameTransitionError.UNKNOWN_PARTICIPANT);
        }
    }

    private void requireStatus(GameStateSnapshot game, GameStatus expected) {
        Objects.requireNonNull(game, "game is required");
        if (game.status() != expected) {
            throw failure(GameTransitionError.ILLEGAL_GAME_TRANSITION);
        }
    }

    private GameTransitionException failure(GameTransitionError error) {
        return new GameTransitionException(error);
    }
}
