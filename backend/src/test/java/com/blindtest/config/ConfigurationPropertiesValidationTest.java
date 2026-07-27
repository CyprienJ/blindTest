package com.blindtest.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.blindtest.config.properties.ApplicationProperties;
import com.blindtest.config.properties.JwtProperties;
import com.blindtest.config.properties.ResendProperties;
import com.blindtest.config.properties.S3Properties;
import com.blindtest.config.properties.TurnstileProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class ConfigurationPropertiesValidationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner().withUserConfiguration(
            PropertiesConfiguration.class).withPropertyValues(
                    "jwt.access-token-ttl-ms=900000",
                    "jwt.email-verification-ttl-ms=86400000",
                    "jwt.password-reset-ttl-ms=3600000",
                    "jwt.refresh-token-ttl-ms=604800000",
                    "resend.api-key=re_test",
                    "cloudflare.turnstile.secret-key=test-key",
                    "app.base-url=https://example.test",
                    "app.media-dir=/media",
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

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({
        JwtProperties.class,
        ResendProperties.class,
        TurnstileProperties.class,
        ApplicationProperties.class,
        S3Properties.class
    })
    static class PropertiesConfiguration {
    }
}
