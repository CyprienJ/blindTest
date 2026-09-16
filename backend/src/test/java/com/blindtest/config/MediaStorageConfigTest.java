package com.blindtest.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.blindtest.config.properties.S3Properties;
import com.blindtest.infra.storage.MinioMediaStorage;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.http.Method;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class MediaStorageConfigTest {

    @Test
    void configuresPrivateOperationsAndPublicSigningFromTypedProperties() throws Exception {
        S3Properties properties = new S3Properties(
                URI.create("http://storage.internal:9000"),
                URI.create("https://media.example.test"),
                "eu-west-3",
                "blindtest-media",
                "configured-access",
                "configured-secret",
                Duration.ofMinutes(5));
        MediaStorageConfig config = new MediaStorageConfig();

        try (var privateClient = config.mediaStorageClient(properties);
                var publicClient = config.mediaPublicUrlClient(properties)) {
            var arguments = GetPresignedObjectUrlArgs.builder().
                    method(Method.GET).
                    bucket(properties.bucket()).
                    object("audio/example.mp3").
                    expiry(300).
                    build();

            String privateUrl = privateClient.getPresignedObjectUrl(arguments);
            String publicUrl = publicClient.getPresignedObjectUrl(arguments);

            assertThat(privateUrl).startsWith("http://storage.internal:9000/blindtest-media/audio/example.mp3?");
            assertThat(publicUrl).startsWith("https://media.example.test/blindtest-media/audio/example.mp3?");
            assertThat(publicUrl).contains("configured-access%2F").contains("%2Feu-west-3%2Fs3%2F");
            assertThat(config.mediaStorage(privateClient, publicClient, properties)).
                    isInstanceOf(MinioMediaStorage.class);
        }
    }
}
