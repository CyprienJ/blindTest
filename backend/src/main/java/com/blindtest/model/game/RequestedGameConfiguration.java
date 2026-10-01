package com.blindtest.model.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Immutable player request awaiting domain validation.
 *
 * <p>The extension map reserves a forward-compatible data shape. V1 rejects every
 * extension rather than silently accepting a future game mode or rule.
 *
 * @param difficultyLevels requested star levels; {@code null} selects every level
 * @param includeUnrated whether tracks without a difficulty are requested
 */
public record RequestedGameConfiguration(
        UUID playlistId,
        int requestedTrackCount,
        int maximumRoundDurationSeconds,
        Map<String, String> extensions,
        List<Integer> difficultyLevels,
        boolean includeUnrated) {

    /** Defensively snapshots the reserved extension data supplied by the caller. */
    public RequestedGameConfiguration {
        Map<String, String> supplied = extensions == null ? Map.of() : extensions;
        extensions = Collections.unmodifiableMap(new LinkedHashMap<>(supplied));
        difficultyLevels = difficultyLevels == null
                ? null : Collections.unmodifiableList(new ArrayList<>(difficultyLevels));
    }

    /** Requests every difficulty level, including unrated tracks. */
    public RequestedGameConfiguration(
            UUID playlistId,
            int requestedTrackCount,
            int maximumRoundDurationSeconds,
            Map<String, String> extensions) {
        this(playlistId, requestedTrackCount, maximumRoundDurationSeconds, extensions, null, true);
    }
}
