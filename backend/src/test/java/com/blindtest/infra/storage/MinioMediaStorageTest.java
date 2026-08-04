package com.blindtest.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.blindtest.config.properties.S3Properties;
import com.blindtest.service.port.MediaStorageException;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.ObjectWriteResponse;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.UploadObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.ErrorResponse;
import io.minio.messages.Item;
import io.minio.Result;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

class MinioMediaStorageTest {

    private static final String BUCKET = "test-media";
    private static final String OBJECT_KEY = "imports/job/audio.mp3";

    @TempDir
    private Path tempDir;

    private MinioClient storageClient;
    private MinioClient publicUrlClient;
    private MinioMediaStorage storage;

    @BeforeEach
    void setUp() {
        storageClient = mock(MinioClient.class);
        publicUrlClient = mock(MinioClient.class);
        storage = new MinioMediaStorage(storageClient, publicUrlClient, properties());
    }

    @Test
    void storesFileAndReturnsProviderNeutralMetadata() throws Exception {
        Path source = tempDir.resolve("audio.mp3");
        Files.write(source, new byte[] {1, 2, 3});
        ObjectWriteResponse response = mock(ObjectWriteResponse.class);
        when(response.etag()).thenReturn("etag-123");
        when(storageClient.uploadObject(any(UploadObjectArgs.class))).thenReturn(response);

        var stored = storage.store(OBJECT_KEY, source, "audio/mpeg");

        assertThat(stored.objectKey()).isEqualTo(OBJECT_KEY);
        assertThat(stored.size()).isEqualTo(3);
        assertThat(stored.entityTag()).isEqualTo("etag-123");
        ArgumentCaptor<UploadObjectArgs> arguments = ArgumentCaptor.forClass(UploadObjectArgs.class);
        verify(storageClient).uploadObject(arguments.capture());
        assertThat(arguments.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(arguments.getValue().object()).isEqualTo(OBJECT_KEY);
        assertThat(arguments.getValue().filename()).isEqualTo(source.toString());
        assertThat(arguments.getValue().contentType().toString()).isEqualTo("audio/mpeg");
    }

    @Test
    void checksObjectExistenceUsingPrivateClient() throws Exception {
        assertThat(storage.exists(OBJECT_KEY)).isTrue();

        ArgumentCaptor<StatObjectArgs> arguments = ArgumentCaptor.forClass(StatObjectArgs.class);
        verify(storageClient).statObject(arguments.capture());
        assertThat(arguments.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(arguments.getValue().object()).isEqualTo(OBJECT_KEY);
    }

    @Test
    void missingObjectIsReportedAsAbsent() throws Exception {
        ErrorResponse error = new ErrorResponse(
                "NoSuchKey", "missing", BUCKET, OBJECT_KEY, null, null, null);
        ErrorResponseException missing = new ErrorResponseException(error, null, null);
        when(storageClient.statObject(any(StatObjectArgs.class))).thenThrow(missing);

        assertThat(storage.exists(OBJECT_KEY)).isFalse();
    }

    @Test
    void providerFailuresAreTranslatedAtPortBoundary() throws Exception {
        ErrorResponse error = new ErrorResponse(
                "AccessDenied", "denied", BUCKET, OBJECT_KEY, null, null, null);
        ErrorResponseException denied = new ErrorResponseException(error, null, null);
        when(storageClient.statObject(any(StatObjectArgs.class))).thenThrow(denied);

        assertThatThrownBy(() -> storage.exists(OBJECT_KEY)).
                isInstanceOf(MediaStorageException.class).
                hasMessage("Media storage operation failed: exists").
                hasCause(denied);
    }

    @Test
    void signsGetUrlUsingPublicClientAndConfiguredTtl() throws Exception {
        when(publicUrlClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class))).
                thenReturn("https://media.example.test/test-media/imports/job/audio.mp3?signature=value");

        URI url = storage.presignedGetUrl(OBJECT_KEY);

        assertThat(url.getHost()).isEqualTo("media.example.test");
        ArgumentCaptor<GetPresignedObjectUrlArgs> arguments =
                ArgumentCaptor.forClass(GetPresignedObjectUrlArgs.class);
        verify(publicUrlClient).getPresignedObjectUrl(arguments.capture());
        assertThat(arguments.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(arguments.getValue().object()).isEqualTo(OBJECT_KEY);
        assertThat(arguments.getValue().expiry()).isEqualTo(300);
    }

    @Test
    void signsGetUrlForAtLeastRequestedValidityAndRoundsUpPartialSeconds()
            throws Exception {
        when(publicUrlClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class))).
                thenReturn("https://media.example.test/audio.mp3?signature=value");

        storage.presignedGetUrl(OBJECT_KEY, Duration.ofMillis(300_001));

        ArgumentCaptor<GetPresignedObjectUrlArgs> arguments =
                ArgumentCaptor.forClass(GetPresignedObjectUrlArgs.class);
        verify(publicUrlClient).getPresignedObjectUrl(arguments.capture());
        assertThat(arguments.getValue().expiry()).isEqualTo(301);
    }

    @Test
    void rejectsNegativeRequestedValidityBeforeCallingProvider() {
        assertThatThrownBy(() -> storage.presignedGetUrl(
                OBJECT_KEY, Duration.ofSeconds(-1))).
                isInstanceOf(IllegalArgumentException.class).
                hasMessageContaining("minimumValidity");
    }

    @Test
    void deletesObjectUsingPrivateClient() throws Exception {
        storage.delete(OBJECT_KEY);

        ArgumentCaptor<RemoveObjectArgs> arguments = ArgumentCaptor.forClass(RemoveObjectArgs.class);
        verify(storageClient).removeObject(arguments.capture());
        assertThat(arguments.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(arguments.getValue().object()).isEqualTo(OBJECT_KEY);
    }

    @Test
    void listsObjectKeysRecursivelyUsingPrivateClient() throws Exception {
        @SuppressWarnings("unchecked")
        Result<Item> result = mock(Result.class);
        Item item = mock(Item.class);
        when(item.objectName()).thenReturn(OBJECT_KEY);
        when(result.get()).thenReturn(item);
        when(storageClient.listObjects(any(ListObjectsArgs.class))).thenReturn(List.of(result));

        assertThat(storage.listObjectKeys()).containsExactly(OBJECT_KEY);

        ArgumentCaptor<ListObjectsArgs> arguments = ArgumentCaptor.forClass(ListObjectsArgs.class);
        verify(storageClient).listObjects(arguments.capture());
        assertThat(arguments.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(arguments.getValue().recursive()).isTrue();
    }

    private S3Properties properties() {
        return new S3Properties(
                URI.create("http://minio:9000"),
                URI.create("https://media.example.test"),
                "eu-west-3",
                BUCKET,
                "test-access",
                "test-secret",
                Duration.ofMinutes(5));
    }
}
