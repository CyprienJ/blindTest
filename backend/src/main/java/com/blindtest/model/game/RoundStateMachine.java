package com.blindtest.model.game;

import java.time.Duration;
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
        requireRunning(round, RoundStatus.PLAYING);
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
        requireRunning(round, RoundStatus.PLAYING);
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
        requireRunning(round, RoundStatus.PLAYING);
        Objects.requireNonNull(evaluatedAt, "evaluatedAt is required");
        Objects.requireNonNull(activeParticipants, "activeParticipants are required");
        requireAtOrAfterStart(evaluatedAt, round.scoringWindow().orElseThrow());
        return resolveIfComplete(round, evaluatedAt, activeParticipants);
    }

    /** Reveals a playing round as timed out only at or after its deadline. */
    public RoundStateSnapshot timeout(RoundStateSnapshot round, Instant timedOutAt) {
        requireRunning(round, RoundStatus.PLAYING);
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

    /** Completes a revealed round at or after its reveal timestamp, even while it is paused. */
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
                Optional.of(completedAt),
                Optional.empty(),
                round.revealPause());
    }

    /**
     * Pauses a playing round before its deadline, freezing its remaining time, or a revealed
     * round, holding the transition to the next track.
     */
    public RoundStateSnapshot pause(RoundStateSnapshot round, Instant pausedAt) {
        Objects.requireNonNull(round, "round is required");
        Objects.requireNonNull(pausedAt, "pausedAt is required");
        if (round.status() != RoundStatus.PLAYING && round.status() != RoundStatus.REVEALED) {
            throw failure(GameTransitionError.ILLEGAL_ROUND_TRANSITION);
        }
        if (round.paused()) {
            throw failure(GameTransitionError.ROUND_PAUSED);
        }
        if (round.status() == RoundStatus.PLAYING) {
            RoundScoringWindow window = round.scoringWindow().orElseThrow();
            requireAtOrAfterStart(pausedAt, window);
            if (!pausedAt.isBefore(window.deadline())) {
                throw failure(GameTransitionError.ROUND_TIME_INVALID);
            }
        } else if (pausedAt.isBefore(round.revealedAt().orElseThrow())) {
            throw failure(GameTransitionError.ROUND_TIME_INVALID);
        }
        return withPause(round, round.scoringWindow(), round.playbackWindow(),
                Optional.of(pausedAt), round.revealPause());
    }

    /**
     * Resumes a paused round. A playing round's deadline moves by the paused time, so its
     * remaining time and title score are unchanged; a reveal's transition is delayed likewise.
     */
    public RoundStateSnapshot resume(RoundStateSnapshot round, Instant resumedAt) {
        Objects.requireNonNull(round, "round is required");
        Objects.requireNonNull(resumedAt, "resumedAt is required");
        Instant pausedAt = round.pausedAt().orElseThrow(
                () -> failure(GameTransitionError.ROUND_NOT_PAUSED));
        if (resumedAt.isBefore(pausedAt)) {
            throw failure(GameTransitionError.ROUND_TIME_INVALID);
        }
        Duration paused = Duration.between(pausedAt, resumedAt);
        if (round.status() == RoundStatus.REVEALED) {
            return withPause(round, round.scoringWindow(), round.playbackWindow(),
                    Optional.empty(), round.revealPause().plus(paused));
        }
        RoundScoringWindow previous = round.scoringWindow().orElseThrow();
        RoundScoringWindow shifted = new RoundScoringWindow(
                previous.deadline().plus(paused), previous.configuredDuration());
        return withPause(round, Optional.of(shifted),
                Optional.of(RoundPlaybackWindow.from(round.track().playback(), shifted)),
                Optional.empty(), round.revealPause());
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
                round.completedAt(),
                round.pausedAt(),
                round.revealPause());
    }

    private RoundStateSnapshot withPause(
            RoundStateSnapshot round,
            Optional<RoundScoringWindow> scoringWindow,
            Optional<RoundPlaybackWindow> playbackWindow,
            Optional<Instant> pausedAt,
            Duration revealPause) {
        return new RoundStateSnapshot(
                round.track(),
                round.status(),
                scoringWindow,
                playbackWindow,
                round.outcome(),
                round.scoreState(),
                round.revealedAt(),
                round.completedAt(),
                pausedAt,
                revealPause);
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

    private void requireRunning(RoundStateSnapshot round, RoundStatus expected) {
        requireStatus(round, expected);
        if (round.paused()) {
            throw failure(GameTransitionError.ROUND_PAUSED);
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
