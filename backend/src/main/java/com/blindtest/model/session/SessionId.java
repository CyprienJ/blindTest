package com.blindtest.model.session;

import java.util.Objects;
import java.util.UUID;

/** Opaque active game-session identity. */
public record SessionId(UUID value) {

    /** Requires a stable non-null identity. */
    public SessionId {
        Objects.requireNonNull(value, "value is required");
    }
}
