package com.blindtest.websocket.protocol;

/** Shared session-event discriminator delivered only to the session topic. */
public enum PublicSessionEventType {
    SESSION_READY(PublicSessionEventPayload.SessionReady.class),
    ROUND_PREPARING(PublicSessionEventPayload.RoundPreparing.class),
    ROUND_STARTED(PublicSessionEventPayload.RoundStarted.class),
    ROUND_REVEALED(PublicSessionEventPayload.RoundRevealed.class),
    SCORE_UPDATED(PublicSessionEventPayload.ScoreUpdated.class),
    GAME_FINISHED(PublicSessionEventPayload.GameFinished.class);

    private final Class<? extends PublicSessionEventPayload> payloadType;

    PublicSessionEventType(Class<? extends PublicSessionEventPayload> payloadType) {
        this.payloadType = payloadType;
    }

    boolean supports(PublicSessionEventPayload payload) {
        return payloadType.isInstance(payload);
    }
}
