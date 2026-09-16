package com.blindtest.model.dto;

import java.time.Instant;
import java.util.UUID;

/** Safe creation response for a new server-authoritative solo session. */
public record SoloSessionResponse(
        UUID sessionId,
        UUID participantId,
        String state,
        long version,
        Instant expiresAt,
        SessionConnectionInstructions connection) {
}
