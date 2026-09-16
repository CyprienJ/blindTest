package com.blindtest.config.properties;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Cloudflare Turnstile server-side verification settings. */
@Validated
@ConfigurationProperties("cloudflare.turnstile")
public record TurnstileProperties(@NotBlank String secretKey) {
}
