package com.blindtest.config.properties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Typed settings used to sign JWTs and control token lifetimes. */
@Validated
@ConfigurationProperties("jwt")
public record JwtProperties(
        @NotBlank String secret,
        @Valid @NotNull TokenSettings accessToken,
        @Valid @NotNull TokenSettings emailVerification,
        @Valid @NotNull TokenSettings passwordReset,
        @Valid @NotNull TokenSettings refreshToken) {

    /** Lifetime settings shared by each token purpose. */
    public record TokenSettings(@Positive long ttlMs) {
    }
}
