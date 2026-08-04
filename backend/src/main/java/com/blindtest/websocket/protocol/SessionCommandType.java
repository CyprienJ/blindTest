package com.blindtest.websocket.protocol;

/** Client-to-server gameplay command discriminator for protocol V1. */
public enum SessionCommandType {
    START_GAME,
    SUBMIT_ANSWER,
    SKIP_ROUND,
    REQUEST_SNAPSHOT
}
