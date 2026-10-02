package com.blindtest.model.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Refresh-safe projection of one durably completed game from one participant's point of view:
 * {@code totalScore}, {@code titlesFound}, and round points are the caller's own, while
 * {@code skippedRounds} and {@code timedOutRounds} count round outcomes.
 */
public record CurrentGameSummaryResponse(
        UUID sessionId,
        int totalScore,
        int totalRounds,
        int titlesFound,
        int skippedRounds,
        int timedOutRounds,
        Instant startedAt,
        Instant completedAt,
        List<GameSummaryRoundResponse> rounds,
        String gameMode,
        UUID participantId,
        List<GameSummaryStandingResponse> standings) {

    /** Defensively retains the authoritative round and ranking order. */
    public CurrentGameSummaryResponse {
        rounds = List.copyOf(rounds);
        standings = List.copyOf(standings);
    }
}
