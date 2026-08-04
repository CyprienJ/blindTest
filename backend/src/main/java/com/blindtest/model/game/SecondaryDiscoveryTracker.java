package com.blindtest.model.game;

import java.util.Objects;
import java.util.Optional;

/** Pure participant-private routing for secondary answer discoveries. */
public final class SecondaryDiscoveryTracker {

    /** Routes a safe match result without changing round lifecycle or score state. */
    public SecondaryDiscoveryResult route(
            SecondaryDiscoveryState state,
            GameParticipantId participantId,
            AnswerMatchResult match) {
        Objects.requireNonNull(state, "state is required");
        Objects.requireNonNull(participantId, "participantId is required");
        Objects.requireNonNull(match, "match is required");

        if (match.matchedType().isEmpty()) {
            return unchanged(SecondaryDiscoveryOutcome.NO_MATCH, state);
        }
        GameAnswerType type = match.matchedType().orElseThrow();
        if (type == GameAnswerType.TITLE) {
            return unchanged(SecondaryDiscoveryOutcome.TITLE_AVAILABLE, state);
        }
        if (state.contains(participantId, type)) {
            return unchanged(SecondaryDiscoveryOutcome.ALREADY_FOUND, state);
        }
        SecondaryDiscoveryState updated = state.withDiscovery(participantId, type);
        return new SecondaryDiscoveryResult(
                SecondaryDiscoveryOutcome.DISCOVERED,
                Optional.of(type),
                updated);
    }

    private SecondaryDiscoveryResult unchanged(
            SecondaryDiscoveryOutcome outcome,
            SecondaryDiscoveryState state) {
        return new SecondaryDiscoveryResult(outcome, Optional.empty(), state);
    }
}
