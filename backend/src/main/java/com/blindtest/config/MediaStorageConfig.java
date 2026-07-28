package com.blindtest.config;

import com.blindtest.config.properties.S3Properties;
import com.blindtest.infra.storage.MinioMediaStorage;
import com.blindtest.service.port.MediaStorage;
import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the S3-compatible media adapter from typed application configuration. */
@Configuration
public class MediaStorageConfig {

    /** Client used for private server-to-storage operations. */
    @Bean
    public MinioClient mediaStorageClient(S3Properties properties) {
        return createClient(properties.endpoint().toString(), properties);
    }

    /** Client used only to sign URLs carrying the browser-accessible endpoint. */
    @Bean
    public MinioClient mediaPublicUrlClient(S3Properties properties) {
        return createClient(properties.publicEndpoint().toString(), properties);
    }

    /** Exposes MinIO only through the provider-neutral media-storage boundary. */
    @Bean
    public MediaStorage mediaStorage(
            @Qualifier("mediaStorageClient") MinioClient mediaStorageClient,
            @Qualifier("mediaPublicUrlClient") MinioClient mediaPublicUrlClient,
            S3Properties properties) {
        return new MinioMediaStorage(mediaStorageClient, mediaPublicUrlClient, properties);
    }

    private MinioClient createClient(String endpoint, S3Properties properties) {
        return MinioClient.builder().
                endpoint(endpoint).
                credentials(properties.accessKey(), properties.secretKey()).
                region(properties.region()).
                build();
    }
}
