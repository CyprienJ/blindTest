package com.blindtest.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.blindtest.config.properties.ApplicationProperties;
import com.blindtest.config.properties.ImportProperties;
import com.blindtest.config.properties.JwtProperties;
import com.blindtest.config.properties.ResendProperties;
import com.blindtest.config.properties.S3Properties;
import com.blindtest.config.properties.TurnstileProperties;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class ConfigurationPropertiesValidationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner().withUserConfiguration(
            PropertiesConfiguration.class).withPropertyValues(
                    "jwt.access-token.ttl-ms=900000",
                    "jwt.email-verification.ttl-ms=86400000",
                    "jwt.password-reset.ttl-ms=3600000",
                    "jwt.refresh-token.ttl-ms=604800000",
                    "resend.api-key=re_test",
                    "cloudflare.turnstile.secret-key=test-key",
                    "app.base-url=https://example.test",
                    "app.media-dir=/media",
                    "app.import.temp-dir=/tmp/blindtest-imports",
                    "app.import.max-size-bytes=26214400",
                    "app.import.timeout=10m",
                    "app.s3.endpoint=http://minio:9000",
                    "app.s3.public-endpoint=https://media.example.test",
                    "app.s3.region=us-east-1",
                    "app.s3.bucket=test-bucket",
                    "app.s3.access-key=test-access",
                    "app.s3.secret-key=test-secret",
                    "app.s3.presigned-url-ttl=5m");

    @Test
    void missingJwtSecretFailsContextStartup() {
        contextRunner.run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasMessageContaining("jwt");
            assertThat(context.getStartupFailure().getMessage()).doesNotContain("test-secret");
        });
    }

    @Test
    void bindsEveryMediaStorageSettingToItsTypedProperty() {
        contextRunner.withPropertyValues("jwt.secret=01234567890123456789012345678901").run(context -> {
            assertThat(context).hasNotFailed();
            S3Properties properties = context.getBean(S3Properties.class);
            assertThat(properties.endpoint()).isEqualTo(URI.create("http://minio:9000"));
            assertThat(properties.publicEndpoint()).isEqualTo(URI.create("https://media.example.test"));
            assertThat(properties.region()).isEqualTo("us-east-1");
            assertThat(properties.bucket()).isEqualTo("test-bucket");
            assertThat(properties.accessKey()).isEqualTo("test-access");
            assertThat(properties.secretKey()).isEqualTo("test-secret");
            assertThat(properties.presignedUrlTtl()).isEqualTo(Duration.ofMinutes(5));
        });
    }

    @Test
    void bindsAndValidatesImportSettings() {
        contextRunner.withPropertyValues("jwt.secret=01234567890123456789012345678901").run(context -> {
            assertThat(context).hasNotFailed();
            ImportProperties properties = context.getBean(ImportProperties.class);
            assertThat(properties.tempDir().toString()).isEqualTo("/tmp/blindtest-imports");
            assertThat(properties.maxSizeBytes()).isEqualTo(26_214_400L);
            assertThat(properties.timeout()).isEqualTo(Duration.ofMinutes(10));
            assertThat(properties.youtubeCookiesFile()).isNull();
        });

        contextRunner.withPropertyValues(
                "jwt.secret=01234567890123456789012345678901",
                "app.import.youtube-cookies-file=/run/secrets/youtube-cookies.txt").run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(ImportProperties.class).youtubeCookiesFile()).
                            isEqualTo(Path.of("/run/secrets/youtube-cookies.txt"));
                });

        contextRunner.withPropertyValues(
                "jwt.secret=01234567890123456789012345678901",
                "app.import.max-size-bytes=0").run(context -> assertThat(context).hasFailed());
        contextRunner.withPropertyValues(
                "jwt.secret=01234567890123456789012345678901",
                "app.import.timeout=0s").run(context -> assertThat(context).hasFailed());
        contextRunner.withPropertyValues(
                "jwt.secret=01234567890123456789012345678901",
                "app.import.youtube-cookies-file=relative/cookies.txt").
                run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({
        JwtProperties.class,
        ResendProperties.class,
        TurnstileProperties.class,
        ApplicationProperties.class,
        ImportProperties.class,
        S3Properties.class
    })
    static class PropertiesConfiguration {
    }
}
