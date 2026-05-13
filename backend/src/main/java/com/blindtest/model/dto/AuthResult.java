package com.blindtest.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Internal carrier returned by {@code AuthService} to the controller,
 * bundling the access token, the opaque refresh token (for the HttpOnly cookie),
 * and an optional user summary (non-null on login, null on refresh).
 */
@Getter
@AllArgsConstructor
public class AuthResult {

    private String accessToken;
    private String refreshToken;
    private UserSummary user;
}
