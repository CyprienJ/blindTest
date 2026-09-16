package com.blindtest.websocket.protocol;

import java.util.Objects;

/** Versioned shared event published only to a session's member-authorized topic. */
public record PublicSessionEvent(
        SessionEventMetadata metadata,
        PublicSessionEventType type,
        PublicSessionEventPayload payload) {

    /** Prevents a discriminator from being paired with a different payload shape. */
    public PublicSessionEvent {
        Objects.requireNonNull(metadata, "metadata is required");
        Objects.requireNonNull(type, "type is required");
        Objects.requireNonNull(payload, "payload is required");
        if (!type.supports(payload)) {
            throw new IllegalArgumentException("public event type does not match payload");
        }
    }
}
