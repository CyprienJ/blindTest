package com.blindtest.websocket.protocol;

import java.util.Objects;
import java.util.UUID;

/**
 * Versioned client command sent to the session command destination.
 *
 * <p>{@code commandId} is the idempotency identity and {@code expectedSessionVersion} is the
 * client's optimistic view. Only {@link SessionCommandType#SUBMIT_ANSWER} accepts {@code answer}.
 */
public record SessionCommand(
        int protocolVersion,
        UUID commandId,
        long expectedSessionVersion,
        SessionCommandType type,
        String answer) {

    /** Enforces protocol, idempotency, optimistic-version, and command-shape invariants. */
    public SessionCommand {
        SessionProtocol.requireProtocolVersion(protocolVersion);
        Objects.requireNonNull(commandId, "commandId is required");
        SessionProtocol.requireNonNegative(expectedSessionVersion, "expectedSessionVersion");
        Objects.requireNonNull(type, "type is required");
        if (type == SessionCommandType.SUBMIT_ANSWER) {
            SessionProtocol.requireText(answer, "answer");
        } else if (answer != null) {
            throw new IllegalArgumentException("answer is only allowed for SUBMIT_ANSWER");
        }
    }
}
