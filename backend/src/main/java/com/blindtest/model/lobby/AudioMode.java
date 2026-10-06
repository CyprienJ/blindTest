package com.blindtest.model.lobby;

/** Which multiplayer devices play the round audio (ADR-0022). */
public enum AudioMode {
    /** Every participant's browser plays the excerpt. */
    EVERYONE,
    /** Only the host's browser plays it, for players gathered in one room. */
    HOST_ONLY
}
