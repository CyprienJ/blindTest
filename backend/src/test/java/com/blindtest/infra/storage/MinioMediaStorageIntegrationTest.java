package com.blindtest.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.blindtest.config.properties.S3Properties;
import com.blindtest.service.port.MediaStorageException;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.containers.wait.strategy.Wait;

@Testcontainers
class MinioMediaStorageIntegrationTest {

    private static final String IMAGE = "minio/minio:RELEASE.2025-09-07T16-13-09Z";
    private static final String ACCESS_KEY = "integration-access";
    private static final String SECRET_KEY = "integration-secret";
    private static final String BUCKET = "integration-media";
    private static final String OBJECT_KEY = "tracks/integration/audio.bin";
    private static final byte[] CONTENT = contentFixture();

    @Container
    private static final GenericContainer<?> MINIO =
            new GenericContainer<>(DockerImageName.parse(IMAGE)).
                    withEnv("MINIO_ROOT_USER", ACCESS_KEY).
                    withEnv("MINIO_ROOT_PASSWORD", SECRET_KEY).
                    withCommand("server", "/data").
                    withExposedPorts(9000).
                    waitingFor(Wait.forHttp("/minio/health/ready").forPort(9000));

    private static MinioMediaStorage storage;

    @TempDir
    private Path tempDir;

    @BeforeAll
    static void createStorage() throws Exception {
        URI endpoint = endpoint();
        MinioClient client = client(endpoint);
        if (!client.bucketExists(BucketExistsArgs.builder().bucket(BUCKET).build())) {
            client.makeBucket(MakeBucketArgs.builder().bucket(BUCKET).build());
        }
        storage = new MinioMediaStorage(client, client, properties(endpoint));
    }

    @AfterEach
    void removeFixture() {
        storage.delete(OBJECT_KEY);
    }

    @Test
    void putExistsListAndDeleteUseRealMinioApi() throws Exception {
        Path source = writeFixture();

        var stored = storage.store(OBJECT_KEY, source, "application/octet-stream");

        assertThat(stored.objectKey()).isEqualTo(OBJECT_KEY);
        assertThat(stored.size()).isEqualTo(CONTENT.length);
        assertThat(stored.entityTag()).isNotBlank();
        assertThat(storage.exists(OBJECT_KEY)).isTrue();
        assertThat(storage.listObjectKeys()).contains(OBJECT_KEY);

        storage.delete(OBJECT_KEY);

        assertThat(storage.exists(OBJECT_KEY)).isFalse();
    }

    @Test
    void presignedGetSupportsHttpByteRanges() throws Exception {
        storage.store(OBJECT_KEY, writeFixture(), "application/octet-stream");
        URI signedUrl = storage.presignedGetUrl(OBJECT_KEY);
        HttpRequest request = HttpRequest.newBuilder(signedUrl).
                header("Range", "bytes=1024-2047").
                GET().
                build();

        HttpResponse<byte[]> response = HttpClient.newHttpClient().
                send(request, HttpResponse.BodyHandlers.ofByteArray());

        assertThat(response.statusCode()).isEqualTo(206);
        assertThat(response.headers().firstValue("accept-ranges")).contains("bytes");
        assertThat(response.headers().firstValue("content-range")).
                contains("bytes 1024-2047/" + CONTENT.length);
        assertThat(response.body()).containsExactly(
                Arrays.copyOfRange(CONTENT, 1024, 2048));
    }

    @Test
    void repeatedPutAndDeleteAreSafeForApplicationRetries() throws Exception {
        Path source = writeFixture();

        storage.store(OBJECT_KEY, source, "application/octet-stream");
        storage.store(OBJECT_KEY, source, "application/octet-stream");
        storage.delete(OBJECT_KEY);
        storage.delete(OBJECT_KEY);

        assertThat(storage.exists(OBJECT_KEY)).isFalse();
    }

    @Test
    void expiredPresignedUrlIsRejectedAndAFreshOneStillWorks() throws Exception {
        // E2E-06 (expired pre-signed URL): a link a client held onto past its TTL must be
        // refused by MinIO rather than silently served, and the underlying object must remain
        // retrievable through a newly issued URL — expiry rejects the link, not the media.
        URI shortLivedEndpoint = endpoint();
        MinioMediaStorage shortLived = new MinioMediaStorage(
                client(shortLivedEndpoint), client(shortLivedEndpoint),
                properties(shortLivedEndpoint, Duration.ofSeconds(1)));
        storage.store(OBJECT_KEY, writeFixture(), "application/octet-stream");

        URI expiring = shortLived.presignedGetUrl(OBJECT_KEY);
        Thread.sleep(Duration.ofSeconds(2));

        HttpResponse<byte[]> expiredResponse = HttpClient.newHttpClient().
                send(HttpRequest.newBuilder(expiring).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        assertThat(expiredResponse.statusCode()).isEqualTo(403);

        URI fresh = storage.presignedGetUrl(OBJECT_KEY);
        HttpResponse<byte[]> freshResponse = HttpClient.newHttpClient().
                send(HttpRequest.newBuilder(fresh).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        assertThat(freshResponse.statusCode()).isEqualTo(200);
        assertThat(freshResponse.body()).containsExactly(CONTENT);
    }

    @Test
    void unavailableStorageIsTranslatedAndTheOperationCanBeRetried() throws Exception {
        URI unavailableEndpoint = URI.create("http://127.0.0.1:1");
        MinioMediaStorage unavailable = new MinioMediaStorage(
                client(unavailableEndpoint), client(unavailableEndpoint), properties(unavailableEndpoint));
        Path source = writeFixture();

        assertThatThrownBy(() -> unavailable.store(OBJECT_KEY, source, "application/octet-stream")).
                isInstanceOf(MediaStorageException.class).
                hasMessage("Media storage operation failed: store");

        var stored = storage.store(OBJECT_KEY, source, "application/octet-stream");

        assertThat(stored.size()).isEqualTo(CONTENT.length);
        assertThat(storage.exists(OBJECT_KEY)).isTrue();
    }

    private Path writeFixture() throws Exception {
        Path source = tempDir.resolve("audio.bin");
        Files.write(source, CONTENT);
        return source;
    }

    private static URI endpoint() {
        return URI.create("http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000));
    }

    private static MinioClient client(URI endpoint) {
        return MinioClient.builder().
                endpoint(endpoint.toString()).
                credentials(ACCESS_KEY, SECRET_KEY).
                build();
    }

    private static S3Properties properties(URI endpoint) {
        return properties(endpoint, Duration.ofMinutes(5));
    }

    private static S3Properties properties(URI endpoint, Duration presignedUrlTtl) {
        return new S3Properties(
                endpoint,
                endpoint,
                "us-east-1",
                BUCKET,
                ACCESS_KEY,
                SECRET_KEY,
                presignedUrlTtl);
    }

    private static byte[] contentFixture() {
        byte[] content = new byte[8192];
        for (int index = 0; index < content.length; index++) {
            content[index] = (byte) (index % 251);
        }
        return content;
    }
}
