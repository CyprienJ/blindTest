package com.blindtest.model.dto;

import com.blindtest.model.entity.InvitationStatus;
import java.time.Instant;
import java.util.UUID;

/** Administrator view of one invitation. The link token is never returned. */
public record InvitationResponse(
        UUID id,
        String email,
        InvitationStatus status,
        String invitedBy,
        Instant createdAt,
        Instant expiresAt,
        Instant acceptedAt,
        Instant revokedAt) {
}
