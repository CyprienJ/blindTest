package com.blindtest.websocket.protocol;

/** Player-private event discriminator delivered only through a user queue. */
public enum PrivateSessionEventType {
    SECONDARY_MATCH(PrivateSessionEventPayload.SecondaryMatch.class, true),
    ANSWER_REJECTED(PrivateSessionEventPayload.AnswerRejected.class, true),
    CURRENT_SNAPSHOT(PrivateSessionEventPayload.CurrentSnapshot.class, true),
    ERROR(PrivateSessionEventPayload.Error.class, false);

    private final Class<? extends PrivateSessionEventPayload> payloadType;
    private final boolean requiresCommandId;

    PrivateSessionEventType(
            Class<? extends PrivateSessionEventPayload> payloadType,
            boolean requiresCommandId) {
        this.payloadType = payloadType;
        this.requiresCommandId = requiresCommandId;
    }

    boolean supports(PrivateSessionEventPayload payload) {
        return payloadType.isInstance(payload);
    }

    boolean requiresCommandId() {
        return requiresCommandId;
    }
}
