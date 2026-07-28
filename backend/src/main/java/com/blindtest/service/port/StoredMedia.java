package com.blindtest.service.port;

/** Provider-neutral metadata returned after a media upload. */
public record StoredMedia(String objectKey, long size, String entityTag) {
}
