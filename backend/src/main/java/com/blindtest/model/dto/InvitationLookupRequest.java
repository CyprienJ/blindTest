package com.blindtest.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request body for {@code POST /api/auth/invitations/lookup}. */
public record InvitationLookupRequest(@NotBlank @Size(max = 128) String token) {
}
