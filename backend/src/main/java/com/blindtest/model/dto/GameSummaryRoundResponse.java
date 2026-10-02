package com.blindtest.model.dto;

import com.blindtest.model.game.RoundOutcome;
import java.time.Instant;

/** Safe immutable result of one completed round; points and titleFound are the caller's. */
public record GameSummaryRoundResponse(
        int roundIndex,
        RoundOutcome outcome,
        int points,
        String title,
        String artist,
        String composer,
        String album,
        Instant startedAt,
        Instant revealedAt,
        Instant completedAt,
        boolean titleFound) {
}
