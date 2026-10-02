package com.blindtest.model.game;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Immutable final score, ordered outcome summary, and ranked participant standings for a
 * completed game. Round points and the total add up every participant's awards.
 */
public record GameSummary(
        int totalScore,
        int totalRounds,
        int titlesFound,
        int skippedRounds,
        int timedOutRounds,
        List<RoundResultSummary> rounds,
        List<ParticipantStanding> standings) {

    /** Defensively copies results and verifies aggregate counts, score, and standings. */
    public GameSummary {
        Objects.requireNonNull(rounds, "rounds are required");
        Objects.requireNonNull(standings, "standings are required");
        rounds = List.copyOf(rounds);
        standings = List.copyOf(standings);
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
        validateStandings(standings, totalScore);
    }

    /** Returns the standing of one participant. */
    public ParticipantStanding standingFor(GameParticipantId participantId) {
        return standings.stream().
                filter(standing -> standing.participantId().equals(participantId)).
                findFirst().
                orElseThrow(() -> new IllegalArgumentException("unknown participant"));
    }

    static GameSummary from(
            List<GameParticipantId> participants,
            List<RoundStateSnapshot> completedRounds) {
        List<RoundResultSummary> results = completedRounds.stream().
                map(RoundResultSummary::from).
                toList();
        int totalScore = results.stream().mapToInt(RoundResultSummary::points).sum();
        int titles = count(results, RoundOutcome.TITLE_FOUND);
        int skipped = count(results, RoundOutcome.SKIPPED);
        int timedOut = count(results, RoundOutcome.TIMEOUT);
        return new GameSummary(
                totalScore, results.size(), titles, skipped, timedOut, results,
                rank(participants, completedRounds));
    }

    /** Orders by score, then join order; equal scores share the higher rank (1, 1, 3). */
    private static List<ParticipantStanding> rank(
            List<GameParticipantId> participants,
            List<RoundStateSnapshot> completedRounds) {
        List<ParticipantStanding> unranked = new ArrayList<>();
        for (GameParticipantId participantId : participants) {
            int score = completedRounds.stream().
                    mapToInt(round -> round.scoreState().pointsFor(participantId)).
                    sum();
            int titles = Math.toIntExact(completedRounds.stream().
                    filter(round -> round.scoreState().titleAwarded(participantId)).
                    count());
            unranked.add(new ParticipantStanding(participantId, score, titles, 1));
        }
        List<ParticipantStanding> ordered = unranked.stream().
                sorted(Comparator.comparingInt(ParticipantStanding::totalScore).reversed()).
                toList();
        List<ParticipantStanding> ranked = new ArrayList<>();
        for (int index = 0; index < ordered.size(); index++) {
            ParticipantStanding standing = ordered.get(index);
            int rank = index > 0 && ordered.get(index - 1).totalScore() == standing.totalScore()
                    ? ranked.get(index - 1).rank()
                    : index + 1;
            ranked.add(new ParticipantStanding(
                    standing.participantId(), standing.totalScore(), standing.titlesFound(), rank));
        }
        return ranked;
    }

    private static void validateStandings(List<ParticipantStanding> standings, int totalScore) {
        if (standings.isEmpty()
                || standings.stream().mapToInt(ParticipantStanding::totalScore).sum() != totalScore
                || standings.stream().map(ParticipantStanding::participantId).distinct().count()
                        != standings.size()) {
            throw new IllegalArgumentException("game summary standings are inconsistent");
        }
    }

    private static int count(List<RoundResultSummary> rounds, RoundOutcome outcome) {
        return Math.toIntExact(rounds.stream().filter(round -> round.outcome() == outcome).count());
    }
}
