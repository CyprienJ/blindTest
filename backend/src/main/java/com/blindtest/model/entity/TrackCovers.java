package com.blindtest.model.entity;

import java.util.UUID;

/** Storage and public locations of a stored track cover. */
public final class TrackCovers {

    private TrackCovers() {
    }

    /** Returns the private object key of a cover. */
    public static String objectKey(UUID coverId) {
        return "covers/" + coverId + ".jpg";
    }

    /** Returns the same-origin path that serves a cover; the random ID is its only secret. */
    public static String publicPath(UUID coverId) {
        return "/api/covers/" + coverId;
    }
}
