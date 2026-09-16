package com.blindtest.config.properties;

import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Public application and local runtime settings. */
@Validated
@ConfigurationProperties("app")
public record ApplicationProperties(@NotNull URI baseUrl, @NotNull Path mediaDir) {
}
