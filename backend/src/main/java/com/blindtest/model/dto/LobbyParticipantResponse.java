package com.blindtest.model.dto;

import java.util.UUID;

/** Public lobby member projection; never exposes account identities. */
public record LobbyParticipantResponse(
        UUID participantId,
        String displayName,
        boolean ready,
        boolean host,
        boolean guest) {
}
