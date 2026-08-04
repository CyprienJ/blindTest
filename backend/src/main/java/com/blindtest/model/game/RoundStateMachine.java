package com.blindtest.model.game;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Pure transition rules for one round; scheduling and transport remain external. */
public final class RoundStateMachine {

    private final TitleScoreCalculator scoreCalculator;

    public RoundStateMachine(TitleScoreCalculator scoreCalculator) {
        this.scoreCalculator = Objects.requireNonNull(
                scoreCalculator, "scoreCalculator is required");
    }

    /** Moves a prepared round to playing with its authoritative scoring window. */
    public RoundStateSnapshot start(
            RoundStateSnapshot round,
            RoundScoringWindow scoringWindow) {
        requireStatus(round, RoundStatus.PREPARING);
        Objects.requireNonNull(scoringWindow, "scoringWindow is required");
        RoundPlaybackWindow playbackWindow =
                RoundPlaybackWindow.from(round.track().playback(), scoringWindow);
        return new RoundStateSnapshot(
                round.track(),
                RoundStatus.PLAYING,
                Optional.of(scoringWindow),
                Optional.of(playbackWindow),
                Optional.empty(),
                round.scoreState(),
                Optional.empty(),
                Optional.empty());
    }

    /** Reveals an in-time title, or resolves timeout when the title arrives too late. */
    public RoundStateSnapshot resolveTitle(
            RoundStateSnapshot round,
            AnswerMatchResult match,
            Instant acceptedAt) {
        requireStatus(round, RoundStatus.PLAYING);
        Objects.requireNonNull(match, "match is required");
        Objects.requireNonNull(acceptedAt, "acceptedAt is required");
        if (match.matchedType().filter(type -> type == GameAnswerType.TITLE).isEmpty()) {
            throw failure(GameTransitionError.TITLE_MATCH_REQUIRED);
        }
        RoundScoringWindow window = round.scoringWindow().orElseThrow();
        requireAtOrAfterStart(acceptedAt, window);
        if (!acceptedAt.isBefore(window.deadline())) {
            return reveal(
                    round,
                    RoundOutcome.TIMEOUT,
                    acceptedAt,
                    scoreCalculator.scoreTimeout(round.scoreState()).state());
        }
        RoundScoreResult score = scoreCalculator.scoreAnswer(
                match, acceptedAt, window, round.scoreState());
        return reveal(
                round, RoundOutcome.TITLE_FOUND, acceptedAt, score.state());
    }

    /** Reveals a playing round as skipped without changing its score. */
    public RoundStateSnapshot skip(RoundStateSnapshot round, Instant skippedAt) {
        requireStatus(round, RoundStatus.PLAYING);
        Objects.requireNonNull(skippedAt, "skippedAt is required");
        RoundScoringWindow window = round.scoringWindow().orElseThrow();
        requireAtOrAfterStart(skippedAt, window);
        RoundOutcome outcome = skippedAt.isBefore(window.deadline())
                ? RoundOutcome.SKIPPED
                : RoundOutcome.TIMEOUT;
        return reveal(
                round,
                outcome,
                skippedAt,
                scoreCalculator.scoreSkip(round.scoreState()).state());
    }

    /** Reveals a playing round as timed out only at or after its deadline. */
    public RoundStateSnapshot timeout(RoundStateSnapshot round, Instant timedOutAt) {
        requireStatus(round, RoundStatus.PLAYING);
        Objects.requireNonNull(timedOutAt, "timedOutAt is required");
        if (timedOutAt.isBefore(round.scoringWindow().orElseThrow().deadline())) {
            throw failure(GameTransitionError.ROUND_DEADLINE_NOT_REACHED);
        }
        return reveal(
                round,
                RoundOutcome.TIMEOUT,
                timedOutAt,
                scoreCalculator.scoreTimeout(round.scoreState()).state());
    }

    /** Completes a revealed round at or after its reveal timestamp. */
    public RoundStateSnapshot complete(RoundStateSnapshot round, Instant completedAt) {
        requireStatus(round, RoundStatus.REVEALED);
        Objects.requireNonNull(completedAt, "completedAt is required");
        if (completedAt.isBefore(round.revealedAt().orElseThrow())) {
            throw failure(GameTransitionError.ROUND_TIME_INVALID);
        }
        return new RoundStateSnapshot(
                round.track(),
                RoundStatus.COMPLETED,
                round.scoringWindow(),
                round.playbackWindow(),
                round.outcome(),
                round.scoreState(),
                round.revealedAt(),
                Optional.of(completedAt));
    }

    private RoundStateSnapshot reveal(
            RoundStateSnapshot round,
            RoundOutcome outcome,
            Instant revealedAt,
            RoundScoreState scoreState) {
        return new RoundStateSnapshot(
                round.track(),
                RoundStatus.REVEALED,
                round.scoringWindow(),
                round.playbackWindow(),
                Optional.of(outcome),
                scoreState,
                Optional.of(revealedAt),
                Optional.empty());
    }

    private void requireStatus(RoundStateSnapshot round, RoundStatus expected) {
        Objects.requireNonNull(round, "round is required");
        if (round.status() != expected) {
            throw failure(GameTransitionError.ILLEGAL_ROUND_TRANSITION);
        }
    }

    private void requireAtOrAfterStart(Instant occurredAt, RoundScoringWindow window) {
        if (occurredAt.isBefore(window.startsAt())) {
            throw failure(GameTransitionError.ROUND_TIME_INVALID);
        }
    }

    private GameTransitionException failure(GameTransitionError error) {
        return new GameTransitionException(error);
    }
}
