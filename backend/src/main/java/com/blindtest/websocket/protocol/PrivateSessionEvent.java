package com.blindtest.websocket.protocol;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Versioned feedback addressed to exactly one authenticated session participant. */
public record PrivateSessionEvent(
        SessionEventMetadata metadata,
        UUID recipientParticipantId,
        Optional<UUID> commandId,
        PrivateSessionEventType type,
        PrivateSessionEventPayload payload) {

    /** Enforces recipient, command correlation, and discriminator/payload invariants. */
    public PrivateSessionEvent {
        Objects.requireNonNull(metadata, "metadata is required");
        Objects.requireNonNull(recipientParticipantId, "recipientParticipantId is required");
        Objects.requireNonNull(commandId, "commandId is required");
        Objects.requireNonNull(type, "type is required");
        Objects.requireNonNull(payload, "payload is required");
        if (!type.supports(payload)) {
            throw new IllegalArgumentException("private event type does not match payload");
        }
        if (type.requiresCommandId() && commandId.isEmpty()) {
            throw new IllegalArgumentException("commandId is required for private command feedback");
        }
    }
}
