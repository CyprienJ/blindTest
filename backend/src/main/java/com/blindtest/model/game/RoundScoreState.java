package com.blindtest.model.game;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable per-round title awards and skip votes, indexed by participant.
 *
 * <p>Each participant can earn at most one title award per round (ADR-0019), which keeps the
 * award idempotent; a solo game is the one-participant case.
 */
public record RoundScoreState(
        Map<GameParticipantId, Integer> titleAwards,
        Set<GameParticipantId> skipVotes) {

    private static final RoundScoreState UNSCORED = new RoundScoreState(Map.of(), Set.of());

    /** Copies the ledgers and accepts only valid V1 title awards. */
    public RoundScoreState {
        Objects.requireNonNull(titleAwards, "titleAwards are required");
        Objects.requireNonNull(skipVotes, "skipVotes are required");
        titleAwards.forEach((participantId, points) -> {
            Objects.requireNonNull(participantId, "participantId is required");
            if (points == null
                    || points < TitleScoreCalculator.MINIMUM_TITLE_POINTS
                    || points > TitleScoreCalculator.MAXIMUM_TITLE_POINTS) {
                throw new IllegalArgumentException("awardedTitlePoints is outside the scoring range");
            }
        });
        skipVotes.forEach(participantId ->
                Objects.requireNonNull(participantId, "participantId is required"));
        titleAwards = Collections.unmodifiableMap(new HashMap<>(titleAwards));
        skipVotes = Collections.unmodifiableSet(new HashSet<>(skipVotes));
    }

    /** Returns the initial state before any title award or skip vote. */
    public static RoundScoreState unscored() {
        return UNSCORED;
    }

    /** Returns a copy retaining one participant's title award. */
    public RoundScoreState withTitleAward(GameParticipantId participantId, int points) {
        Objects.requireNonNull(participantId, "participantId is required");
        if (titleAwarded(participantId)) {
            throw new IllegalArgumentException("participant already has a title award");
        }
        Map<GameParticipantId, Integer> awards = new HashMap<>(titleAwards);
        awards.put(participantId, points);
        return new RoundScoreState(awards, skipVotes);
    }

    /** Returns a copy retaining one participant's skip vote; repeated votes are no-ops. */
    public RoundScoreState withSkipVote(GameParticipantId participantId) {
        Objects.requireNonNull(participantId, "participantId is required");
        if (skipVotes.contains(participantId)) {
            return this;
        }
        Set<GameParticipantId> votes = new HashSet<>(skipVotes);
        votes.add(participantId);
        return new RoundScoreState(titleAwards, votes);
    }

    /** Returns whether any participant has found the title in this round. */
    public boolean titleAwarded() {
        return !titleAwards.isEmpty();
    }

    /** Returns whether the participant has found the title in this round. */
    public boolean titleAwarded(GameParticipantId participantId) {
        return titleAwards.containsKey(participantId);
    }

    /** Returns the participant's title points for this round, or zero. */
    public int pointsFor(GameParticipantId participantId) {
        return titleAwards.getOrDefault(participantId, 0);
    }

    /** Returns whether the participant voted to skip this round. */
    public boolean skipVoted(GameParticipantId participantId) {
        return skipVotes.contains(participantId);
    }

    /** Returns the points awarded to every participant in this round. */
    public int totalPoints() {
        return titleAwards.values().stream().mapToInt(Integer::intValue).sum();
    }

    /** Returns the stable rule version to retain in session and history snapshots. */
    public String scoringRuleVersion() {
        return TitleScoreCalculator.SCORING_RULE_VERSION;
    }
}
