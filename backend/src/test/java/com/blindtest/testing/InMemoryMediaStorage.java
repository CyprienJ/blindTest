package com.blindtest.testing;

import com.blindtest.service.port.MediaStorage;
import com.blindtest.service.port.StoredMedia;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** In-memory media-storage adapter for unit and integration tests. */
public class InMemoryMediaStorage implements MediaStorage {

    private final Map<String, StoredObject> objects = new LinkedHashMap<>();
    private final URI publicEndpoint;

    /** Creates an empty store using a deterministic public endpoint. */
    public InMemoryMediaStorage() {
        this(URI.create("https://media.example.test"));
    }

    /** Creates an empty store using the supplied public endpoint. */
    public InMemoryMediaStorage(URI publicEndpoint) {
        this.publicEndpoint = publicEndpoint;
    }

    @Override
    public synchronized StoredMedia store(String objectKey, Path source, String contentType) {
        try {
            byte[] content = Files.readAllBytes(source);
            objects.put(objectKey, new StoredObject(content, contentType));
            return new StoredMedia(objectKey, content.length, "in-memory-" + content.length);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read media fixture", exception);
        }
    }

    @Override
    public synchronized Optional<byte[]> read(String objectKey) {
        StoredObject stored = objects.get(objectKey);
        return stored == null ? Optional.empty() : Optional.of(stored.content().clone());
    }

    @Override
    public synchronized boolean exists(String objectKey) {
        return objects.containsKey(objectKey);
    }

    @Override
    public synchronized URI presignedGetUrl(String objectKey) {
        if (!exists(objectKey)) {
            throw new IllegalArgumentException("Unknown media object");
        }
        return publicEndpoint.resolve("/" + objectKey + "?signature=test");
    }

    @Override
    public synchronized URI presignedGetUrl(String objectKey, Duration minimumValidity) {
        if (minimumValidity == null || minimumValidity.isNegative()) {
            throw new IllegalArgumentException("minimumValidity cannot be negative");
        }
        return presignedGetUrl(objectKey);
    }

    @Override
    public synchronized void delete(String objectKey) {
        objects.remove(objectKey);
    }

    @Override
    public synchronized Set<String> listObjectKeys() {
        return Set.copyOf(objects.keySet());
    }

    /** Removes every captured object. */
    public synchronized void clear() {
        objects.clear();
    }

    /** Returns a copy of the bytes stored under an object key. */
    public synchronized byte[] content(String objectKey) {
        StoredObject stored = objects.get(objectKey);
        if (stored == null) {
            throw new IllegalArgumentException("Unknown media object");
        }
        return stored.content().clone();
    }

    /** Returns the captured content type for an object. */
    public synchronized String contentType(String objectKey) {
        StoredObject stored = objects.get(objectKey);
        if (stored == null) {
            throw new IllegalArgumentException("Unknown media object");
        }
        return stored.contentType();
    }

    private record StoredObject(byte[] content, String contentType) {
    }
}
