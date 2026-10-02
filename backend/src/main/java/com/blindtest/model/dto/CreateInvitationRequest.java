package com.blindtest.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request body for {@code POST /api/admin/invitations}. */
public record CreateInvitationRequest(
        @NotBlank @Email @Size(max = 255) String email) {
}
