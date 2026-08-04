package com.blindtest.model.dto;

import java.util.Map;
import java.util.UUID;

/** Requested configuration for a new authenticated solo session. */
public record CreateSoloSessionRequest(
        UUID playlistId,
        int trackCount,
        int maxRoundSeconds,
        Map<String, String> extensions) {
}
