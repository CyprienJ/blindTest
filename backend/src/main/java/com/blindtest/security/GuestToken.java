package com.blindtest.security;

import java.util.Objects;
import java.util.UUID;

/** Verified claims of a guest participant token (ADR-0020): one participant of one session. */
public record GuestToken(UUID sessionId, UUID participantId) {

    /** Requires both identities. */
    public GuestToken {
        Objects.requireNonNull(sessionId, "sessionId is required");
        Objects.requireNonNull(participantId, "participantId is required");
    }
}
