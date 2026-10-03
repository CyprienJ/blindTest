package com.blindtest.websocket.protocol;

/** Client-to-server gameplay command discriminator for protocol V1. */
public enum SessionCommandType {
    START_GAME,
    SUBMIT_ANSWER,
    SKIP_ROUND,
    ADVANCE_ROUND,
    PAUSE_ROUND,
    RESUME_ROUND,
    REQUEST_SNAPSHOT
}
