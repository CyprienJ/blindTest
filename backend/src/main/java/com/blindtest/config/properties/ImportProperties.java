package com.blindtest.config.properties;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Temporary workspace and validation limits for downloaded audio imports. */
@Validated
@ConfigurationProperties("app.import")
public record ImportProperties(
        @NotNull Path tempDir,
        @Positive long maxSizeBytes,
        @NotNull Duration timeout,
        Path youtubeCookiesFile) {

    /** Rejects zero, negative, and impractically long process lifetimes. */
    public ImportProperties {
        if (timeout != null && (timeout.isZero() || timeout.isNegative()
                || timeout.compareTo(Duration.ofHours(1)) > 0)) {
            throw new IllegalArgumentException("Import timeout must be positive and no longer than one hour");
        }
        if (youtubeCookiesFile != null && !youtubeCookiesFile.isAbsolute()) {
            throw new IllegalArgumentException("YouTube cookies file must use an absolute path");
        }
    }
}
