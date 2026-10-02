package com.blindtest.model.game;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

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

    /**
     * Awards one participant's in-time title, then reveals the round once every active
     * participant has found it; a title arriving at or after the deadline resolves timeout.
     */
    public RoundStateSnapshot awardTitle(
            RoundStateSnapshot round,
            GameParticipantId participantId,
            AnswerMatchResult match,
            Instant acceptedAt,
            Set<GameParticipantId> activeParticipants) {
        requireStatus(round, RoundStatus.PLAYING);
        Objects.requireNonNull(participantId, "participantId is required");
        Objects.requireNonNull(match, "match is required");
        Objects.requireNonNull(acceptedAt, "acceptedAt is required");
        Objects.requireNonNull(activeParticipants, "activeParticipants are required");
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
        if (round.scoreState().titleAwarded(participantId)) {
            throw failure(GameTransitionError.TITLE_ALREADY_FOUND);
        }
        RoundScoreResult score = scoreCalculator.scoreAnswer(
                match, acceptedAt, window, participantId, round.scoreState());
        return resolveIfComplete(withScoreState(round, score.state()), acceptedAt, activeParticipants);
    }

    /**
     * Records one participant's skip vote and reveals the round as skipped once the active
     * participants' votes reach {@link SkipVoteRule}; a vote at or after the deadline times out.
     */
    public RoundStateSnapshot voteSkip(
            RoundStateSnapshot round,
            GameParticipantId participantId,
            Instant votedAt,
            Set<GameParticipantId> activeParticipants) {
        requireStatus(round, RoundStatus.PLAYING);
        Objects.requireNonNull(participantId, "participantId is required");
        Objects.requireNonNull(votedAt, "votedAt is required");
        Objects.requireNonNull(activeParticipants, "activeParticipants are required");
        RoundScoringWindow window = round.scoringWindow().orElseThrow();
        requireAtOrAfterStart(votedAt, window);
        if (!votedAt.isBefore(window.deadline())) {
            return reveal(
                    round,
                    RoundOutcome.TIMEOUT,
                    votedAt,
                    scoreCalculator.scoreTimeout(round.scoreState()).state());
        }
        RoundScoreState voted = scoreCalculator.scoreSkip(round.scoreState()).state().
                withSkipVote(participantId);
        return resolveIfComplete(withScoreState(round, voted), votedAt, activeParticipants);
    }

    /**
     * Re-checks completion after the active participant set changed, without a command:
     * reveals when every remaining participant found the title or the skip vote now passes.
     */
    public RoundStateSnapshot reevaluate(
            RoundStateSnapshot round,
            Instant evaluatedAt,
            Set<GameParticipantId> activeParticipants) {
        requireStatus(round, RoundStatus.PLAYING);
        Objects.requireNonNull(evaluatedAt, "evaluatedAt is required");
        Objects.requireNonNull(activeParticipants, "activeParticipants are required");
        requireAtOrAfterStart(evaluatedAt, round.scoringWindow().orElseThrow());
        return resolveIfComplete(round, evaluatedAt, activeParticipants);
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

    private RoundStateSnapshot resolveIfComplete(
            RoundStateSnapshot round,
            Instant at,
            Set<GameParticipantId> activeParticipants) {
        RoundScoreState state = round.scoreState();
        if (!activeParticipants.isEmpty()
                && activeParticipants.stream().allMatch(state::titleAwarded)) {
            return reveal(round, RoundOutcome.TITLE_FOUND, at, state);
        }
        if (SkipVoteRule.passes(state, activeParticipants)) {
            return reveal(round, RoundOutcome.SKIPPED, at, state);
        }
        return round;
    }

    private RoundStateSnapshot withScoreState(RoundStateSnapshot round, RoundScoreState state) {
        return new RoundStateSnapshot(
                round.track(),
                round.status(),
                round.scoringWindow(),
                round.playbackWindow(),
                round.outcome(),
                state,
                round.revealedAt(),
                round.completedAt());
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
