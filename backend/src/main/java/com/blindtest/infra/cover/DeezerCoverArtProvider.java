package com.blindtest.infra.cover;

import com.blindtest.model.entity.CoverSource;
import com.blindtest.service.port.CoverArtProvider;
import com.blindtest.service.port.CoverArtQuery;
import com.blindtest.service.port.CoverArtResult;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Searches the public Deezer API, which needs no key.
 *
 * <p>Fielded queries such as {@code artist:"..."} are unreliable, so plain-text searches are
 * used: albums first when an album is known, then tracks.
 */
@Component
public class DeezerCoverArtProvider implements CoverArtProvider {

    private static final String API = "https://api.deezer.com";

    private final CoverHttpClient httpClient;

    /** Creates the provider over the shared bounded cover HTTP client. */
    public DeezerCoverArtProvider(CoverHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public CoverSource source() {
        return CoverSource.DEEZER;
    }

    @Override
    public List<CoverArtResult> search(CoverArtQuery query) throws IOException {
        List<CoverArtResult> results = new ArrayList<>();
        if (query.hasAlbum()) {
            results.addAll(parseAlbums(httpClient.getJson(albumSearchUri(query))));
        }
        results.addAll(parseTracks(httpClient.getJson(trackSearchUri(query))));
        return results;
    }

    static URI albumSearchUri(CoverArtQuery query) {
        return URI.create(API + "/search/album?limit=5&q="
                + CoverQueries.encode(query.artist() + " " + query.album()));
    }

    static URI trackSearchUri(CoverArtQuery query) {
        return URI.create(API + "/search?limit=10&q="
                + CoverQueries.encode(query.artist() + " " + query.title()));
    }

    static List<CoverArtResult> parseAlbums(JsonNode root) {
        List<CoverArtResult> results = new ArrayList<>();
        for (JsonNode album : root.path("data")) {
            result(album.path("title").asText(), album.path("artist").path("name").asText(),
                    album).ifPresent(results::add);
        }
        return results;
    }

    static List<CoverArtResult> parseTracks(JsonNode root) {
        List<CoverArtResult> results = new ArrayList<>();
        for (JsonNode track : root.path("data")) {
            JsonNode album = track.path("album");
            result(album.path("title").asText(), track.path("artist").path("name").asText(),
                    album).ifPresent(results::add);
        }
        return results;
    }

    private static Optional<CoverArtResult> result(
            String album, String artist, JsonNode covers) {
        return CoverQueries.httpsUri(covers.path("cover_medium").asText()).flatMap(thumbnail ->
                CoverQueries.httpsUri(covers.path("cover_xl").asText()).map(image ->
                        new CoverArtResult(CoverSource.DEEZER, album, artist, thumbnail, image)));
    }
}
