package com.blindtest.model.dto;

import com.blindtest.model.entity.UserRole;
import java.time.Instant;
import java.util.UUID;

/** Administrator view of one account. */
public record AdminUserResponse(
        UUID id,
        String username,
        String email,
        UserRole role,
        boolean emailVerified,
        Instant createdAt,
        Instant lastConnexion,
        long gamesPlayed,
        long playlistCount) {
}
