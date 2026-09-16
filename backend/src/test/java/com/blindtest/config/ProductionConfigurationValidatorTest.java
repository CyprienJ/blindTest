package com.blindtest.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.blindtest.config.properties.ApplicationProperties;
import com.blindtest.config.properties.JwtProperties;
import com.blindtest.config.properties.JwtProperties.TokenSettings;
import com.blindtest.config.properties.ResendProperties;
import com.blindtest.config.properties.S3Properties;
import com.blindtest.config.properties.TurnstileProperties;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;

class ProductionConfigurationValidatorTest {

    private static final String DATABASE_PASSWORD = "database-secret-value";
    private static final String JWT_SECRET = "production-jwt-secret-with-32-bytes";
    private static final String RESEND_KEY = "re_production-secret-value";
    private static final String TURNSTILE_KEY = "production-turnstile-secret-value";
    private static final String S3_SECRET = "production-s3-secret-value";

    @Test
    void acceptsValidProductionConfiguration() {
        assertThatCode(this::validValidator).doesNotThrowAnyException();
    }

    @Test
    void rejectsUnsafeProductionConfigurationWithoutExposingValues() {
        JwtProperties jwt = jwt("éééééééééééééééééééééééééééééééé");
        ResendProperties resend = new ResendProperties("re_xxxxxxxxxxxxxxxxx");
        TurnstileProperties turnstile =
                new TurnstileProperties("1x0000000000000000000000000000000AA");
        ApplicationProperties application =
                new ApplicationProperties(URI.create("http://public.example.test"), Path.of("/media"));
        S3Properties s3 = new S3Properties(
                URI.create("file:///private"),
                URI.create("http://media.example.test"),
                "us-east-1",
                "blindtest-media",
                "change_me_s3_access_key",
                "change_me_s3_secret_key",
                Duration.ofMinutes(30));
        DataSourceProperties datasource = datasource("jdbc:h2:mem:test", "", "change_me_database_password");

        ProductionConfigurationValidator validator = new ProductionConfigurationValidator(
                jwt, resend, turnstile, application, s3, datasource);

        Throwable failure = catchThrowable(validator::afterPropertiesSet);

        assertThat(failure).isInstanceOf(IllegalStateException.class);
        assertThat(failure).hasMessageContaining(
                "jwt.secret", "resend.api-key", "cloudflare.turnstile.secret-key");
        assertThat(failure).hasMessageContaining(
                "app.base-url", "spring.datasource.url", "app.s3.endpoint");
        assertThat(failure).hasMessageContaining(
                "app.s3.public-endpoint", "app.s3.presigned-url-ttl");
        assertThat(failure).hasMessageContaining(
                "spring.datasource.password", "app.s3.access-key", "app.s3.secret-key");
        assertThat(failure.getMessage()).doesNotContain(
                "re_xxxxxxxxxxxxxxxxx", "change_me_database_password", "change_me_s3_secret_key");
    }

    private ProductionConfigurationValidator validValidator() {
        JwtProperties jwt = jwt(JWT_SECRET);
        ResendProperties resend = new ResendProperties(RESEND_KEY);
        TurnstileProperties turnstile = new TurnstileProperties(TURNSTILE_KEY);
        ApplicationProperties application =
                new ApplicationProperties(URI.create("https://blindtest.example"), Path.of("/media"));
        S3Properties s3 = new S3Properties(
                URI.create("http://minio:9000"),
                URI.create("https://media.example"),
                "us-east-1",
                "blindtest-media",
                "access-value",
                S3_SECRET,
                Duration.ofMinutes(5));
        DataSourceProperties datasource = datasource(
                "jdbc:postgresql://postgres:5432/blindtest", "blindtest", DATABASE_PASSWORD);
        return new ProductionConfigurationValidator(jwt, resend, turnstile, application, s3, datasource);
    }

    private static DataSourceProperties datasource(String url, String username, String password) {
        DataSourceProperties properties = new DataSourceProperties();
        properties.setUrl(url);
        properties.setUsername(username);
        properties.setPassword(password);
        return properties;
    }

    private static JwtProperties jwt(String secret) {
        return new JwtProperties(
                secret,
                new TokenSettings(900_000),
                new TokenSettings(86_400_000),
                new TokenSettings(3_600_000),
                new TokenSettings(604_800_000));
    }
}
