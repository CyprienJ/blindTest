package com.blindtest.model.game;

import java.util.Objects;

/** Points awarded by one scoring action and the resulting immutable round state. */
public record RoundScoreResult(int pointsAwarded, RoundScoreState state) {

    /** Protects the relationship between a positive award and its retained state. */
    public RoundScoreResult {
        Objects.requireNonNull(state, "state is required");
        if (pointsAwarded < 0 || pointsAwarded > TitleScoreCalculator.MAXIMUM_TITLE_POINTS) {
            throw new IllegalArgumentException("pointsAwarded is outside the scoring range");
        }
        if (pointsAwarded > 0 && !state.titleAwarded()) {
            throw new IllegalArgumentException("a positive award must be retained in state");
        }
    }

    /** Returns whether this action awarded title points. */
    public boolean awardedNow() {
        return pointsAwarded > 0;
    }

    /** Returns the stable scoring-rule version used by this result. */
    public String scoringRuleVersion() {
        return state.scoringRuleVersion();
    }

    static RoundScoreResult noAward(RoundScoreState state) {
        return new RoundScoreResult(0, state);
    }

    static RoundScoreResult titleAward(
            RoundScoreState state,
            GameParticipantId participantId,
            int points) {
        return new RoundScoreResult(points, state.withTitleAward(participantId, points));
    }
}
