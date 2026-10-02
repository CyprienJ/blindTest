package com.blindtest.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Request body for {@code POST /api/auth/register}. */
@Getter
@Setter
@NoArgsConstructor
public class RegisterRequest {

    @NotBlank
    @Size(min = 3, max = 50)
    private String username;

    /** One-time token from the invitation link; it also determines the account email. */
    @NotBlank
    @Size(max = 128)
    private String inviteToken;

    @NotBlank
    @Size(min = 8, max = 128)
    private String password;

    @NotBlank
    private String turnstileToken;
}
