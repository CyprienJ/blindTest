package com.blindtest.config.properties;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Temporary workspace and validation limits for downloaded audio imports. */
@Validated
@ConfigurationProperties("app.import")
public record ImportProperties(
        @NotNull Path tempDir,
        @Positive long maxSizeBytes) {
}
