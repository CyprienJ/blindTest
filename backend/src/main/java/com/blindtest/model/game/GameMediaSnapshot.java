package com.blindtest.model.game;

/** Immutable private-media reference; {@code version} is the finalized content checksum. */
public record GameMediaSnapshot(String objectKey, String version) {

    /** Rejects incomplete private-media references. */
    public GameMediaSnapshot {
        if (objectKey == null || objectKey.isBlank()) {
            throw new IllegalArgumentException("objectKey is required");
        }
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("version is required");
        }
    }
}
