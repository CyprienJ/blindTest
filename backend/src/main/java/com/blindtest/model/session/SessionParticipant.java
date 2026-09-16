package com.blindtest.model.session;

import com.blindtest.model.game.GameParticipantId;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Immutable active-session participant independent from authentication method. */
public record SessionParticipant(
        GameParticipantId participantId,
        Optional<UUID> accountId,
        String displayName) {

    /** Validates the participant projection while allowing future guests. */
    public SessionParticipant {
        Objects.requireNonNull(participantId, "participantId is required");
        Objects.requireNonNull(accountId, "accountId is required");
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("displayName is required");
        }
    }
}
