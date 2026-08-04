package com.blindtest.model.game;

import java.util.List;
import java.util.Objects;

/** Immutable final score and ordered outcome summary for a completed game. */
public record GameSummary(
        int totalScore,
        int totalRounds,
        int titlesFound,
        int skippedRounds,
        int timedOutRounds,
        List<RoundResultSummary> rounds) {

    /** Defensively copies round results and verifies aggregate counts and score. */
    public GameSummary {
        Objects.requireNonNull(rounds, "rounds are required");
        rounds = List.copyOf(rounds);
        if (totalScore < 0 || totalRounds <= 0
                || titlesFound < 0 || skippedRounds < 0 || timedOutRounds < 0
                || totalRounds != rounds.size()
                || titlesFound + skippedRounds + timedOutRounds != totalRounds) {
            throw new IllegalArgumentException("game summary totals are inconsistent");
        }
        int calculatedScore = rounds.stream().mapToInt(RoundResultSummary::points).sum();
        if (calculatedScore != totalScore) {
            throw new IllegalArgumentException("game summary score is inconsistent");
        }
        for (int index = 0; index < rounds.size(); index++) {
            if (rounds.get(index).roundIndex() != index) {
                throw new IllegalArgumentException("game summary rounds must be ordered");
            }
        }
    }

    static GameSummary from(List<RoundStateSnapshot> completedRounds) {
        List<RoundResultSummary> results = completedRounds.stream().
                map(RoundResultSummary::from).
                toList();
        int totalScore = results.stream().mapToInt(RoundResultSummary::points).sum();
        int titles = count(results, RoundOutcome.TITLE_FOUND);
        int skipped = count(results, RoundOutcome.SKIPPED);
        int timedOut = count(results, RoundOutcome.TIMEOUT);
        return new GameSummary(
                totalScore, results.size(), titles, skipped, timedOut, results);
    }

    private static int count(List<RoundResultSummary> rounds, RoundOutcome outcome) {
        return Math.toIntExact(rounds.stream().filter(round -> round.outcome() == outcome).count());
    }
}
