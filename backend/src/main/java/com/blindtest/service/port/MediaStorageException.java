package com.blindtest.service.port;

/** Signals an object-storage operation failure without leaking provider SDK types. */
public class MediaStorageException extends RuntimeException {

    /** Creates a storage failure for the named operation. */
    public MediaStorageException(String operation, Throwable cause) {
        super("Media storage operation failed: " + operation, cause);
    }
}
