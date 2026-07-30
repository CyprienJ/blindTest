package com.blindtest.infra.storage;

import com.blindtest.config.properties.S3Properties;
import com.blindtest.service.port.MediaStorage;
import com.blindtest.service.port.MediaStorageException;
import com.blindtest.service.port.StoredMedia;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.UploadObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;
import io.minio.messages.Item;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/** MinIO Java SDK implementation of the private media-storage boundary. */
public class MinioMediaStorage implements MediaStorage {

    private static final Set<String> MISSING_OBJECT_CODES = Set.of(
            "NoSuchKey",
            "NoSuchObject",
            "NotFound");

    private final MinioClient storageClient;
    private final MinioClient publicUrlClient;
    private final S3Properties properties;

    /** Creates the adapter with separate internal-operation and public-URL clients. */
    public MinioMediaStorage(
            MinioClient storageClient,
            MinioClient publicUrlClient,
            S3Properties properties) {
        this.storageClient = storageClient;
        this.publicUrlClient = publicUrlClient;
        this.properties = properties;
    }

    @Override
    public StoredMedia store(String objectKey, Path source, String contentType) {
        try {
            long size = Files.size(source);
            var response = storageClient.uploadObject(UploadObjectArgs.builder().
                    bucket(properties.bucket()).
                    object(objectKey).
                    filename(source.toString()).
                    contentType(contentType).
                    build());
            return new StoredMedia(objectKey, size, response.etag());
        } catch (Exception exception) {
            throw new MediaStorageException("store", exception);
        }
    }

    @Override
    public boolean exists(String objectKey) {
        try {
            storageClient.statObject(StatObjectArgs.builder().
                    bucket(properties.bucket()).
                    object(objectKey).
                    build());
            return true;
        } catch (ErrorResponseException exception) {
            if (MISSING_OBJECT_CODES.contains(exception.errorResponse().code())) {
                return false;
            }
            throw new MediaStorageException("exists", exception);
        } catch (Exception exception) {
            throw new MediaStorageException("exists", exception);
        }
    }

    @Override
    public URI presignedGetUrl(String objectKey) {
        try {
            String url = publicUrlClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder().
                    method(Method.GET).
                    bucket(properties.bucket()).
                    object(objectKey).
                    expiry(Math.toIntExact(properties.presignedUrlTtl().toSeconds())).
                    build());
            return URI.create(url);
        } catch (Exception exception) {
            throw new MediaStorageException("presign", exception);
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            storageClient.removeObject(RemoveObjectArgs.builder().
                    bucket(properties.bucket()).
                    object(objectKey).
                    build());
        } catch (Exception exception) {
            throw new MediaStorageException("delete", exception);
        }
    }

    @Override
    public Set<String> listObjectKeys() {
        try {
            Set<String> objectKeys = new HashSet<>();
            Iterable<io.minio.Result<Item>> results = storageClient.listObjects(
                    ListObjectsArgs.builder().bucket(properties.bucket()).recursive(true).build());
            for (io.minio.Result<Item> result : results) {
                objectKeys.add(result.get().objectName());
            }
            return Set.copyOf(objectKeys);
        } catch (Exception exception) {
            throw new MediaStorageException("list", exception);
        }
    }
}
