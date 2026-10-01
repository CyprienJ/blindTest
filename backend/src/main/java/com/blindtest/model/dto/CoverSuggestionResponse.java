package com.blindtest.model.dto;

import com.blindtest.model.entity.CoverSource;
import java.util.UUID;

/**
 * A cover the administrator can choose. Provider URLs stay on the server.
 *
 * @param candidateId opaque ID, valid for 30 minutes, for the thumbnail and track update
 */
public record CoverSuggestionResponse(
        UUID candidateId,
        CoverSource source,
        String album,
        String artist) {
}
