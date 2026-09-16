package com.blindtest.model.game;

import java.util.Objects;
import java.util.UUID;

/** Opaque game participant identity independent from accounts and authentication. */
public record GameParticipantId(UUID value) {

    /** Requires a stable non-null participant identity. */
    public GameParticipantId {
        Objects.requireNonNull(value, "value is required");
    }
}
