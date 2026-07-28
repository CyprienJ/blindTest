package com.blindtest.service.port;

import java.net.URI;
import java.nio.file.Path;

/** Provider-neutral boundary for private audio object storage. */
public interface MediaStorage {

    /** Uploads a local media file under the requested object key. */
    StoredMedia store(String objectKey, Path source, String contentType);

    /** Returns whether an object currently exists. */
    boolean exists(String objectKey);

    /** Creates a short-lived browser-accessible GET URL for an object. */
    URI presignedGetUrl(String objectKey);

    /** Deletes an object. Deleting a missing object is idempotent. */
    void delete(String objectKey);
}
