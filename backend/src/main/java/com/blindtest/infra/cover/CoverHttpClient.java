package com.blindtest.infra.cover;

import com.blindtest.config.properties.ApplicationProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Bounded HTTP access to the cover-art catalogues and their image CDNs.
 *
 * <p>Redirects are followed manually so every hop of an image download stays on an HTTPS
 * host from {@link #IMAGE_HOST_SUFFIXES}; the server never fetches an arbitrary URL.
 */
@Component
public class CoverHttpClient {

    /** Largest image accepted from a provider or an administrator upload. */
    public static final int MAX_IMAGE_BYTES = 5 * 1024 * 1024;

    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);
    static final Set<String> IMAGE_HOST_SUFFIXES = Set.of(
            "dzcdn.net", "mzstatic.com", "coverartarchive.org", "archive.org");
    private static final int MAX_JSON_BYTES = 2 * 1024 * 1024;
    private static final int MAX_REDIRECTS = 4;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String userAgent;

    /** Creates the production client; MusicBrainz requires an identifying User-Agent. */
    @Autowired
    public CoverHttpClient(ApplicationProperties applicationProperties, ObjectMapper objectMapper) {
        this(HttpClient.newBuilder().
                connectTimeout(CONNECT_TIMEOUT).
                followRedirects(HttpClient.Redirect.NEVER).
                build(),
                objectMapper,
                "Blindtest/1.0 ( " + applicationProperties.baseUrl() + " )");
    }

    CoverHttpClient(HttpClient httpClient, ObjectMapper objectMapper, String userAgent) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.userAgent = userAgent;
    }

    /** Fetches and parses a JSON document; any non-200 answer is an error. */
    public JsonNode getJson(URI uri) throws IOException {
        HttpResponse<InputStream> response = send(request(uri).
                header("Accept", "application/json").
                GET().
                build());
        try (InputStream body = response.body()) {
            if (response.statusCode() != 200) {
                throw new IOException("Cover catalogue answered HTTP " + response.statusCode());
            }
            return objectMapper.readTree(readBounded(body, MAX_JSON_BYTES));
        }
    }

    /** Returns whether a HEAD request finds the resource, counting redirects as found. */
    public boolean exists(URI uri) throws IOException {
        HttpResponse<InputStream> response = send(request(uri).
                method("HEAD", HttpRequest.BodyPublishers.noBody()).
                build());
        response.body().close();
        int status = response.statusCode();
        return status == 200 || status >= 300 && status < 400;
    }

    /** Downloads an image from an allowed host, following allowed redirects only. */
    public byte[] downloadImage(URI uri) throws IOException {
        URI current = uri;
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            if (!isAllowedImageUri(current)) {
                throw new IOException("Cover image host is not allowed");
            }
            HttpResponse<InputStream> response = send(request(current).GET().build());
            try (InputStream body = response.body()) {
                int status = response.statusCode();
                if (status == 200) {
                    long declared = response.headers().firstValueAsLong("Content-Length").orElse(-1);
                    if (declared > MAX_IMAGE_BYTES) {
                        throw new IOException("Cover image is too large");
                    }
                    return readBounded(body, MAX_IMAGE_BYTES);
                }
                if (status < 300 || status >= 400) {
                    throw new IOException("Cover image answered HTTP " + status);
                }
                String location = response.headers().firstValue("Location").
                        orElseThrow(() -> new IOException("Cover image redirect has no location"));
                current = current.resolve(location);
            }
        }
        throw new IOException("Cover image redirected too many times");
    }

    /** Accepts only HTTPS URLs on a known cover CDN, without credentials or custom ports. */
    static boolean isAllowedImageUri(URI uri) {
        if (uri == null || !"https".equalsIgnoreCase(uri.getScheme())
                || uri.getRawUserInfo() != null || uri.getHost() == null
                || uri.getPort() != -1 && uri.getPort() != 443) {
            return false;
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        return IMAGE_HOST_SUFFIXES.stream().
                anyMatch(suffix -> host.equals(suffix) || host.endsWith("." + suffix));
    }

    private HttpRequest.Builder request(URI uri) {
        return HttpRequest.newBuilder(uri).
                timeout(REQUEST_TIMEOUT).
                header("User-Agent", userAgent);
    }

    private HttpResponse<InputStream> send(HttpRequest request) throws IOException {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Cover request was interrupted", exception);
        }
    }

    private static byte[] readBounded(InputStream input, int maxBytes) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) {
                throw new IOException("Cover response exceeded its size limit");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }
}
