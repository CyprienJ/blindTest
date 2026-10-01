package com.blindtest.model.dto;

import com.blindtest.model.entity.TrackEntity;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Optional public listening links for a track. Blank values mean "no link".
 *
 * <p>Each link must be an HTTP(S) URL on the matching platform's domain.
 */
public record TrackStreamingLinks(
        @Size(max = 500)
        @Pattern(regexp = DEEZER, flags = Pattern.Flag.CASE_INSENSITIVE)
        String deezer,
        @Size(max = 500)
        @Pattern(regexp = YOUTUBE, flags = Pattern.Flag.CASE_INSENSITIVE)
        String youtube,
        @Size(max = 500)
        @Pattern(regexp = SPOTIFY, flags = Pattern.Flag.CASE_INSENSITIVE)
        String spotify,
        @Size(max = 500)
        @Pattern(regexp = QOBUZ, flags = Pattern.Flag.CASE_INSENSITIVE)
        String qobuz,
        @Size(max = 500)
        @Pattern(regexp = TIDAL, flags = Pattern.Flag.CASE_INSENSITIVE)
        String tidal,
        @Size(max = 500)
        @Pattern(regexp = SOUNDCLOUD, flags = Pattern.Flag.CASE_INSENSITIVE)
        String soundcloud) {

    private static final String PREFIX = "^\\s*$|^https?://([a-z0-9-]+\\.)*(";
    private static final String SUFFIX = ")(/\\S*)?$";
    static final String DEEZER = PREFIX + "deezer\\.com|deezer\\.page\\.link|dzr\\.page\\.link" + SUFFIX;
    static final String YOUTUBE = PREFIX + "youtube\\.com|youtu\\.be" + SUFFIX;
    static final String SPOTIFY = PREFIX + "spotify\\.com|spotify\\.link" + SUFFIX;
    static final String QOBUZ = PREFIX + "qobuz\\.com" + SUFFIX;
    static final String TIDAL = PREFIX + "tidal\\.com" + SUFFIX;
    static final String SOUNDCLOUD = PREFIX + "soundcloud\\.com|snd\\.sc" + SUFFIX;

    /** Reads the stored links of the given track. */
    public static TrackStreamingLinks from(TrackEntity entity) {
        return new TrackStreamingLinks(
                entity.getDeezerUrl(), entity.getStreamingYoutubeUrl(), entity.getSpotifyUrl(),
                entity.getQobuzUrl(), entity.getTidalUrl(), entity.getSoundcloudUrl());
    }

    /** Replaces every stored link of the given track; blank values clear the link. */
    public void applyTo(TrackEntity entity) {
        entity.setDeezerUrl(blankToNull(deezer));
        entity.setStreamingYoutubeUrl(blankToNull(youtube));
        entity.setSpotifyUrl(blankToNull(spotify));
        entity.setQobuzUrl(blankToNull(qobuz));
        entity.setTidalUrl(blankToNull(tidal));
        entity.setSoundcloudUrl(blankToNull(soundcloud));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
