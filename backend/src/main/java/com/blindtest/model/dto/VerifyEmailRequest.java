package com.blindtest.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Request body for {@code POST /api/auth/verify-email}. */
@Getter
@Setter
@NoArgsConstructor
public class VerifyEmailRequest {

    @NotBlank
    private String token;
}
