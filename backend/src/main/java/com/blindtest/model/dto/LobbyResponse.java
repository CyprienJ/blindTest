package com.blindtest.model.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Lobby state returned to one member, including that member's own participant identity. */
public record LobbyResponse(
        UUID sessionId,
        String code,
        UUID participantId,
        UUID hostParticipantId,
        List<LobbyParticipantResponse> participants,
        boolean canStart,
        int maxParticipants,
        long version,
        Instant expiresAt,
        SessionConnectionInstructions connection,
        String playlistName,
        int trackCount,
        int maxRoundSeconds) {
}
