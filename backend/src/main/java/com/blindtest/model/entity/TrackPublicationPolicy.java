package com.blindtest.model.entity;

/** Catalogue rule defining the minimum metadata required before publication. */
public final class TrackPublicationPolicy {

    private TrackPublicationPolicy() {
    }

    /**
     * Returns whether a track has its primary answer, display artist, and playable media.
     *
     * <p>Album, composer, work, category, decade, duration, and image are optional secondary
     * metadata. Object-backed media is complete only when its key, checksum, and positive size
     * are all present. A legacy audio URL remains publishable until media migration is complete.
     */
    public static boolean isComplete(TrackEntity track) {
        return hasText(track.getTitle())
                && hasText(track.getArtist())
                && (hasText(track.getAudioUrl()) || hasCompleteObjectMedia(track));
    }

    private static boolean hasCompleteObjectMedia(TrackEntity track) {
        return hasText(track.getAudioObjectKey())
                && hasText(track.getAudioChecksum())
                && track.getAudioSizeBytes() != null
                && track.getAudioSizeBytes() > 0;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
