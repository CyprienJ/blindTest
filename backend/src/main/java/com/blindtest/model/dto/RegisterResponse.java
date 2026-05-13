package com.blindtest.model.dto;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** Response body for a successful {@code POST /api/auth/register}. */
@Getter
@AllArgsConstructor
public class RegisterResponse {

    private UUID id;
    private String username;
    private String email;
}
