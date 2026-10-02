package com.blindtest.model.dto;

import java.time.Instant;

/** Public details of a usable invitation, shown on the registration form. */
public record InvitationLookupResponse(String email, Instant expiresAt) {
}
