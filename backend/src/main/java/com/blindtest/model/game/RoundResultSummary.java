package com.blindtest.model.game;

import java.util.Objects;
import java.util.UUID;

/** Immutable ordered result of one completed round; points add up every participant. */
public record RoundResultSummary(
        int roundIndex,
        UUID trackId,
        RoundOutcome outcome,
        int points,
        String scoringRuleVersion) {

    /** Creates a safe final-round projection from a completed round snapshot. */
    public RoundResultSummary {
        if (roundIndex < 0) {
            throw new IllegalArgumentException("roundIndex cannot be negative");
        }
        Objects.requireNonNull(trackId, "trackId is required");
        Objects.requireNonNull(outcome, "outcome is required");
        if (points < 0 || points > TitleScoreCalculator.MAXIMUM_TITLE_POINTS
                * GameStateSnapshot.MAXIMUM_PARTICIPANTS) {
            throw new IllegalArgumentException("points is outside the scoring range");
        }
        // Points can accompany a skip or timeout once several participants play.
        if (outcome == RoundOutcome.TITLE_FOUND
                && points < TitleScoreCalculator.MINIMUM_TITLE_POINTS) {
            throw new IllegalArgumentException("round outcome and points must agree");
        }
        Objects.requireNonNull(scoringRuleVersion, "scoringRuleVersion is required");
    }

    static RoundResultSummary from(RoundStateSnapshot round) {
        return new RoundResultSummary(
                round.track().playOrder(),
                round.track().trackId(),
                round.outcome().orElseThrow(),
                round.scoreState().totalPoints(),
                round.scoreState().scoringRuleVersion());
    }
}
