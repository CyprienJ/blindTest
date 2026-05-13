package com.blindtest.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** Response body for {@code POST /api/auth/refresh}. */
@Getter
@AllArgsConstructor
public class RefreshResponse {

    private String accessToken;
}
