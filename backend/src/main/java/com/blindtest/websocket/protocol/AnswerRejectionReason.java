package com.blindtest.websocket.protocol;

/** Stable private reason explaining why an answer command was not accepted. */
public enum AnswerRejectionReason {
    WRONG_ANSWER,
    ALREADY_FOUND,
    COOLDOWN,
    STALE_SESSION_VERSION,
    INVALID_ROUND_STATE,
    DUPLICATE_COMMAND
}
