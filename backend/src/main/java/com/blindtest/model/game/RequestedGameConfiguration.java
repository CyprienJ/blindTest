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
 * @param allTracks whether to play every matching track; validation then replaces
 *     {@code requestedTrackCount} with the matching count of the catalogue at that moment
 */
public record RequestedGameConfiguration(
        UUID playlistId,
        int requestedTrackCount,
        int maximumRoundDurationSeconds,
        Map<String, String> extensions,
        List<Integer> difficultyLevels,
        boolean includeUnrated,
        boolean allTracks) {

    /** Defensively snapshots the reserved extension data supplied by the caller. */
    public RequestedGameConfiguration {
        Map<String, String> supplied = extensions == null ? Map.of() : extensions;
        extensions = Collections.unmodifiableMap(new LinkedHashMap<>(supplied));
        difficultyLevels = difficultyLevels == null
                ? null : Collections.unmodifiableList(new ArrayList<>(difficultyLevels));
    }

    /** Requests an explicit track count. */
    public RequestedGameConfiguration(
            UUID playlistId,
            int requestedTrackCount,
            int maximumRoundDurationSeconds,
            Map<String, String> extensions,
            List<Integer> difficultyLevels,
            boolean includeUnrated) {
        this(
                playlistId, requestedTrackCount, maximumRoundDurationSeconds, extensions,
                difficultyLevels, includeUnrated, false);
    }

    /** Requests every difficulty level, including unrated tracks. */
    public RequestedGameConfiguration(
            UUID playlistId,
            int requestedTrackCount,
            int maximumRoundDurationSeconds,
            Map<String, String> extensions) {
        this(playlistId, requestedTrackCount, maximumRoundDurationSeconds, extensions, null, true);
    }

    /** Returns this request with an explicit track count, keeping every other setting. */
    public RequestedGameConfiguration withTrackCount(int trackCount) {
        return new RequestedGameConfiguration(
                playlistId, trackCount, maximumRoundDurationSeconds, extensions,
                difficultyLevels, includeUnrated, allTracks);
    }
}
