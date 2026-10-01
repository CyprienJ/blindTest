package com.blindtest.model.entity;

/** Catalogue rule defining the minimum metadata required before publication. */
public final class TrackPublicationPolicy {

    public static final int MINIMUM_EXCERPT_DURATION_SECONDS = 5;
    /** Matches the longest approved round duration. */
    public static final int MAXIMUM_EXCERPT_DURATION_SECONDS = 60;

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

    /**
     * Returns whether the configured excerpt window is playable.
     *
     * <p>The window length must be between {@link #MINIMUM_EXCERPT_DURATION_SECONDS} and
     * {@link #MAXIMUM_EXCERPT_DURATION_SECONDS}. Offset zero needs no known media duration.
     * A non-zero offset requires the whole window to fit in the known media duration.
     */
    public static boolean hasPlayableExcerpt(TrackEntity track) {
        int window = track.getExcerptDurationSeconds();
        if (window < MINIMUM_EXCERPT_DURATION_SECONDS
                || window > MAXIMUM_EXCERPT_DURATION_SECONDS) {
            return false;
        }
        int offset = track.getExcerptStartSeconds();
        if (offset == 0) {
            return true;
        }
        Integer duration = track.getDurationSeconds();
        return offset > 0
                && duration != null
                && (long) duration - offset >= window;
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
