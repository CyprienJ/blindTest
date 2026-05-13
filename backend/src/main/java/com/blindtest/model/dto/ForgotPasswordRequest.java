package com.blindtest.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Request body for {@code POST /api/auth/forgot-password}. */
@Getter
@Setter
@NoArgsConstructor
public class ForgotPasswordRequest {

    @NotBlank
    @Email
    private String email;
}
