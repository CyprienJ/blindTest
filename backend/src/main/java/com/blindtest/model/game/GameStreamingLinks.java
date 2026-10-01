package com.blindtest.model.game;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Public listening links frozen with a selected track and shown when it is revealed.
 *
 * @param links available links keyed by lowercase platform name, in display order
 */
public record GameStreamingLinks(Map<String, String> links) {

    /** A track without any streaming link. */
    public static final GameStreamingLinks NONE = new GameStreamingLinks(Map.of());

    private static final Pattern WEB_URL = Pattern.compile("^https?://\\S+$",
            Pattern.CASE_INSENSITIVE);

    /** Defensively snapshots the links, preserving their display order. */
    public GameStreamingLinks {
        Objects.requireNonNull(links, "links are required");
        links = Collections.unmodifiableMap(new LinkedHashMap<>(links));
    }

    /** Keeps only present HTTP(S) links so clients never render another URL scheme. */
    public static GameStreamingLinks of(
            String deezer,
            String youtube,
            String spotify,
            String qobuz,
            String tidal,
            String soundcloud) {
        Map<String, String> links = new LinkedHashMap<>();
        put(links, "deezer", deezer);
        put(links, "youtube", youtube);
        put(links, "spotify", spotify);
        put(links, "qobuz", qobuz);
        put(links, "tidal", tidal);
        put(links, "soundcloud", soundcloud);
        return links.isEmpty() ? NONE : new GameStreamingLinks(links);
    }

    private static void put(Map<String, String> links, String platform, String url) {
        if (url != null && WEB_URL.matcher(url.trim()).matches()) {
            links.put(platform, url.trim());
        }
    }
}
