package com.blindtest.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Cover-art suggestion settings.
 *
 * @param suggestionsEnabled whether external catalogues are searched; uploads always work
 */
@ConfigurationProperties("app.covers")
public record CoverProperties(@DefaultValue("true") boolean suggestionsEnabled) {
}
