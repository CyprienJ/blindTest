package com.blindtest.model.lobby;

import com.blindtest.model.game.GameParticipantId;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** One lobby member in join order; guests (MP-08) have no account. */
public record LobbyParticipant(
        GameParticipantId participantId,
        Optional<UUID> accountId,
        String displayName,
        boolean ready) {

    /** Validates identity and display name. */
    public LobbyParticipant {
        Objects.requireNonNull(participantId, "participantId is required");
        Objects.requireNonNull(accountId, "accountId is required");
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("displayName is required");
        }
    }

    /**
     * Returns the identity this member authenticates with: the account ID, or for a guest the
     * participant ID that its guest token carries.
     */
    public UUID principalId() {
        return accountId.orElse(participantId.value());
    }

    /** Returns whether this member signed in with an account. */
    public boolean registered() {
        return accountId.isPresent();
    }

    LobbyParticipant withReady(boolean nextReady) {
        return new LobbyParticipant(participantId, accountId, displayName, nextReady);
    }
}
