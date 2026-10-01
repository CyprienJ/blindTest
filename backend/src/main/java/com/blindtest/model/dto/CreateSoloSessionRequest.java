package com.blindtest.model.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Requested configuration for a new authenticated solo session.
 *
 * @param difficultyLevels star levels to include; {@code null} includes every level
 * @param includeUnrated whether tracks without a difficulty are included; {@code null} means yes
 */
public record CreateSoloSessionRequest(
        UUID playlistId,
        int trackCount,
        int maxRoundSeconds,
        Map<String, String> extensions,
        List<Integer> difficultyLevels,
        Boolean includeUnrated) {

    /** Requests every difficulty level, including unrated tracks. */
    public CreateSoloSessionRequest(
            UUID playlistId, int trackCount, int maxRoundSeconds, Map<String, String> extensions) {
        this(playlistId, trackCount, maxRoundSeconds, extensions, null, null);
    }
}
