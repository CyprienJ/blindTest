package com.blindtest.model.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Refresh-safe owner projection of one durably completed game. */
public record CurrentGameSummaryResponse(
        UUID sessionId,
        int totalScore,
        int totalRounds,
        int titlesFound,
        int skippedRounds,
        int timedOutRounds,
        Instant startedAt,
        Instant completedAt,
        List<GameSummaryRoundResponse> rounds) {

    /** Defensively retains the authoritative round order. */
    public CurrentGameSummaryResponse {
        rounds = List.copyOf(rounds);
    }
}
