package com.blindtest.config.properties;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Credentials for the Resend transactional-email provider. */
@Validated
@ConfigurationProperties("resend")
public record ResendProperties(@NotBlank String apiKey) {
}
