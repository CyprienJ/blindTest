package com.blindtest.model.validation;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/** Parsed, canonical YouTube video URL accepted by the media import pipeline. */
public record YouTubeVideoUrl(String videoId) {

    private static final Pattern VIDEO_ID = Pattern.compile("[A-Za-z0-9_-]{11}");
    private static final Set<String> WATCH_HOSTS = Set.of(
            "youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com");
    private static final String SHORT_HOST = "youtu.be";

    /** Parses supported HTTPS watch, short-link, and Shorts URL forms. */
    public static Optional<YouTubeVideoUrl> parse(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            URI uri = new URI(value);
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || uri.getHost() == null
                    || uri.getUserInfo() != null
                    || uri.getPort() != -1
                    || uri.getFragment() != null) {
                return Optional.empty();
            }
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            String candidate = SHORT_HOST.equals(host)
                    ? shortLinkVideoId(uri)
                    : watchHostVideoId(uri, host);
            return validVideoId(candidate)
                    ? Optional.of(new YouTubeVideoUrl(candidate))
                    : Optional.empty();
        } catch (URISyntaxException exception) {
            return Optional.empty();
        }
    }

    /** Returns the stable URL persisted and passed to yt-dlp. */
    public String canonicalUrl() {
        return "https://www.youtube.com/watch?v=" + videoId;
    }

    private static String shortLinkVideoId(URI uri) {
        String path = uri.getPath();
        if (path == null || path.length() != 12 || path.charAt(0) != '/') {
            return null;
        }
        return path.substring(1);
    }

    private static String watchHostVideoId(URI uri, String host) {
        if (!WATCH_HOSTS.contains(host)) {
            return null;
        }
        if ("/watch".equals(uri.getPath())) {
            return queryVideoId(uri.getRawQuery());
        }
        String path = uri.getPath();
        if (path != null && path.startsWith("/shorts/") && path.length() == 19) {
            return path.substring("/shorts/".length());
        }
        return null;
    }

    private static String queryVideoId(String query) {
        if (query == null) {
            return null;
        }
        String candidate = null;
        for (String parameter : query.split("&")) {
            int separator = parameter.indexOf('=');
            if (separator > 0 && "v".equals(parameter.substring(0, separator))) {
                if (candidate != null) {
                    return null;
                }
                candidate = parameter.substring(separator + 1);
            }
        }
        return candidate;
    }

    private static boolean validVideoId(String candidate) {
        return candidate != null && VIDEO_ID.matcher(candidate).matches();
    }
}
