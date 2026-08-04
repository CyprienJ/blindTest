package com.blindtest.model.game;

/** Immutable per-round state that makes the single title score award idempotent. */
public record RoundScoreState(Integer awardedTitlePoints) {

    private static final RoundScoreState UNSCORED = new RoundScoreState(null);

    /** Accepts either an unscored state or one valid V1 title award. */
    public RoundScoreState {
        if (awardedTitlePoints != null
                && (awardedTitlePoints < TitleScoreCalculator.MINIMUM_TITLE_POINTS
                        || awardedTitlePoints > TitleScoreCalculator.MAXIMUM_TITLE_POINTS)) {
            throw new IllegalArgumentException("awardedTitlePoints is outside the scoring range");
        }
    }

    /** Returns the initial state before a correct title is awarded. */
    public static RoundScoreState unscored() {
        return UNSCORED;
    }

    /** Returns a state containing the one awarded title score. */
    public static RoundScoreState titleAwarded(int points) {
        return new RoundScoreState(points);
    }

    /** Returns whether this round has already awarded its title score. */
    public boolean titleAwarded() {
        return awardedTitlePoints != null;
    }

    /** Returns the round's retained score, or zero before an award. */
    public int totalPoints() {
        return awardedTitlePoints == null ? 0 : awardedTitlePoints;
    }

    /** Returns the stable rule version to retain in session and history snapshots. */
    public String scoringRuleVersion() {
        return TitleScoreCalculator.SCORING_RULE_VERSION;
    }
}
