package com.blindtest.model.game;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Immutable player request awaiting domain validation.
 *
 * <p>The extension map reserves a forward-compatible data shape. V1 rejects every
 * extension rather than silently accepting a future game mode or rule.
 */
public record RequestedGameConfiguration(
        UUID playlistId,
        int requestedTrackCount,
        int maximumRoundDurationSeconds,
        Map<String, String> extensions) {

    /** Defensively snapshots the reserved extension data supplied by the caller. */
    public RequestedGameConfiguration {
        Map<String, String> supplied = extensions == null ? Map.of() : extensions;
        extensions = Collections.unmodifiableMap(new LinkedHashMap<>(supplied));
    }
}
