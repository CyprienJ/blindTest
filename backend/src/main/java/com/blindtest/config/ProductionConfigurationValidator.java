package com.blindtest.config;

import com.blindtest.config.properties.ApplicationProperties;
import com.blindtest.config.properties.JwtProperties;
import com.blindtest.config.properties.ResendProperties;
import com.blindtest.config.properties.S3Properties;
import com.blindtest.config.properties.TurnstileProperties;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Applies production-only semantic checks without including configured values in failures. */
@Component
@Profile("production")
public class ProductionConfigurationValidator implements InitializingBean {

    private static final Duration MAX_PRESIGNED_URL_TTL = Duration.ofMinutes(15);

    private final JwtProperties jwt;
    private final ResendProperties resend;
    private final TurnstileProperties turnstile;
    private final ApplicationProperties application;
    private final S3Properties s3;
    private final DataSourceProperties datasource;

    /** Creates the validator for all security-sensitive production settings. */
    public ProductionConfigurationValidator(
            JwtProperties jwt,
            ResendProperties resend,
            TurnstileProperties turnstile,
            ApplicationProperties application,
            S3Properties s3,
            DataSourceProperties datasource) {
        this.jwt = jwt;
        this.resend = resend;
        this.turnstile = turnstile;
        this.application = application;
        this.s3 = s3;
        this.datasource = datasource;
    }

    @Override
    public void afterPropertiesSet() {
        List<String> invalid = new ArrayList<>();
        require(jwt.secret().getBytes(StandardCharsets.US_ASCII).length >= 32
                && StandardCharsets.US_ASCII.newEncoder().canEncode(jwt.secret())
                && !isPlaceholder(jwt.secret()), "jwt.secret", invalid);
        require(resend.apiKey().startsWith("re_") && resend.apiKey().length() > 3
                && !isPlaceholder(resend.apiKey()),
                "resend.api-key", invalid);
        require(!isCloudflareTestKey(turnstile.secretKey()) && !isPlaceholder(turnstile.secretKey()),
                "cloudflare.turnstile.secret-key", invalid);
        require(isHttps(application.baseUrl()), "app.base-url", invalid);
        require(datasource.getUrl() != null && datasource.getUrl().startsWith("jdbc:postgresql:"),
                "spring.datasource.url", invalid);
        require(StringUtils.hasText(datasource.getUsername()), "spring.datasource.username", invalid);
        require(StringUtils.hasText(datasource.getPassword()) && !isPlaceholder(datasource.getPassword()),
                "spring.datasource.password", invalid);
        require(isHttp(s3.endpoint()), "app.s3.endpoint", invalid);
        require(isHttps(s3.publicEndpoint()), "app.s3.public-endpoint", invalid);
        require(!isPlaceholder(s3.accessKey()), "app.s3.access-key", invalid);
        require(!isPlaceholder(s3.secretKey()), "app.s3.secret-key", invalid);
        require(!s3.presignedUrlTtl().isNegative() && !s3.presignedUrlTtl().isZero()
                && s3.presignedUrlTtl().compareTo(MAX_PRESIGNED_URL_TTL) <= 0,
                "app.s3.presigned-url-ttl", invalid);
        if (!invalid.isEmpty()) {
            throw new IllegalStateException(
                    "Invalid production configuration. Check required setting(s): "
                            + String.join(", ", invalid));
        }
    }

    private static boolean isCloudflareTestKey(String secretKey) {
        return secretKey.startsWith("1x0000000000000000000000000000000")
                || secretKey.startsWith("2x0000000000000000000000000000000")
                || secretKey.startsWith("3x0000000000000000000000000000000");
    }

    private static boolean isPlaceholder(String value) {
        String normalized = value.toLowerCase();
        return normalized.contains("change_me")
                || normalized.contains("xxxxxxxx")
                || normalized.startsWith("your_")
                || normalized.startsWith("dev-secret");
    }

    private static boolean isHttps(URI uri) {
        return "https".equalsIgnoreCase(uri.getScheme()) && StringUtils.hasText(uri.getHost());
    }

    private static boolean isHttp(URI uri) {
        String scheme = uri.getScheme();
        return ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                && StringUtils.hasText(uri.getHost());
    }

    private static void require(boolean valid, String property, List<String> invalid) {
        if (!valid) {
            invalid.add(property);
        }
    }
}
