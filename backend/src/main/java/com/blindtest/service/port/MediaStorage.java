package com.blindtest.service.port;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;

/** Provider-neutral boundary for private audio and cover-image object storage. */
public interface MediaStorage {

    /** Uploads a local media file under the requested object key. */
    StoredMedia store(String objectKey, Path source, String contentType);

    /**
     * Reads a whole object into memory, or returns empty when it does not exist.
     *
     * <p>Intended only for small objects such as normalized cover images.
     */
    Optional<byte[]> read(String objectKey);

    /** Returns whether an object currently exists. */
    boolean exists(String objectKey);

    /** Creates a short-lived browser-accessible GET URL for an object. */
    URI presignedGetUrl(String objectKey);

    /** Creates a GET URL valid for at least the requested duration. */
    URI presignedGetUrl(String objectKey, Duration minimumValidity);

    /** Deletes an object. Deleting a missing object is idempotent. */
    void delete(String objectKey);

    /** Lists object keys for operational reconciliation. */
    Set<String> listObjectKeys();
}
