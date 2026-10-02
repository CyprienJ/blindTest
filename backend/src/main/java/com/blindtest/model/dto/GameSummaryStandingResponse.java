package com.blindtest.model.dto;

import java.util.UUID;

/** One participant's final score and shared rank in a completed game. */
public record GameSummaryStandingResponse(
        UUID participantId,
        String displayName,
        int finalScore,
        int rank,
        boolean guest) {
}
