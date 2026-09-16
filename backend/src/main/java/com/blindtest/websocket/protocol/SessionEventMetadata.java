package com.blindtest.websocket.protocol;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Authoritative identity, ordering, and timestamp metadata shared by every session event. */
public record SessionEventMetadata(
        int protocolVersion,
        UUID eventId,
        UUID sessionId,
        long sessionVersion,
        Instant occurredAt) {

    /** Enforces supported protocol and authoritative ordering metadata. */
    public SessionEventMetadata {
        SessionProtocol.requireProtocolVersion(protocolVersion);
        Objects.requireNonNull(eventId, "eventId is required");
        Objects.requireNonNull(sessionId, "sessionId is required");
        SessionProtocol.requireNonNegative(sessionVersion, "sessionVersion");
        SessionProtocol.requireInstant(occurredAt, "occurredAt");
    }
}
