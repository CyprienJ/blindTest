package com.blindtest.model.game;

import java.util.Objects;

/** One participant's final total, titles found, and shared competition rank. */
public record ParticipantStanding(
        GameParticipantId participantId,
        int totalScore,
        int titlesFound,
        int rank) {

    /** Validates the standing's identity and non-negative totals. */
    public ParticipantStanding {
        Objects.requireNonNull(participantId, "participantId is required");
        if (totalScore < 0 || titlesFound < 0 || rank < 1) {
            throw new IllegalArgumentException("participant standing is inconsistent");
        }
    }
}
