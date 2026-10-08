package com.blindtest.model.dto;

import com.blindtest.model.lobby.AudioMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Lobby state returned to one member, including that member's own participant identity and
 * the settings the host can change before starting.
 *
 * @param difficultyLevels requested star levels; {@code null} includes every level
 */
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
        UUID playlistId,
        String playlistName,
        int trackCount,
        int maxRoundSeconds,
        List<Integer> difficultyLevels,
        boolean includeUnrated,
        AudioMode audioMode) {
}
