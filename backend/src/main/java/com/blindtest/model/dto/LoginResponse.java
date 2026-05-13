package com.blindtest.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** Response body for {@code POST /api/auth/login}. The refresh token is set as an HttpOnly cookie. */
@Getter
@AllArgsConstructor
public class LoginResponse {

    private String accessToken;
    private UserSummary user;
}
