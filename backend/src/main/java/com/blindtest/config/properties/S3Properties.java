package com.blindtest.config.properties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Typed connection and URL-signing settings for S3-compatible private media storage. */
@Validated
@ConfigurationProperties("app.s3")
public record S3Properties(
        @NotNull URI endpoint,
        @NotNull URI publicEndpoint,
        @NotBlank String region,
        @NotBlank String bucket,
        @NotBlank String accessKey,
        @NotBlank String secretKey,
        @NotNull Duration presignedUrlTtl) {
}
