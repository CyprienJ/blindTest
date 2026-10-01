package com.blindtest.infra.cover;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/** Small helpers shared by the cover-art providers. */
final class CoverQueries {

    private CoverQueries() {
    }

    static String encode(String value) {
        return URLEncoder.encode(value.strip(), StandardCharsets.UTF_8);
    }

    /** Parses an HTTPS URL from a provider response, ignoring missing or malformed values. */
    static Optional<URI> httpsUri(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            URI uri = URI.create(value.strip());
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
                    ? Optional.of(uri)
                    : Optional.empty();
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    /** Escapes a phrase for a quoted Lucene term in a MusicBrainz query. */
    static String lucenePhrase(String value) {
        return "\"" + value.strip().replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
