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
import org.springframework.stereotype.Component;

/**
 * Searches the public iTunes Search API, which needs no key.
 *
 * <p>Artwork URLs end with a size such as {@code 100x100bb.jpg}; the size is rewritten to get
 * a 250 px thumbnail and a 600 px image.
 */
@Component
public class ItunesCoverArtProvider implements CoverArtProvider {

    private static final String API = "https://itunes.apple.com/search?media=music";
    private static final String ARTWORK_SIZE = "/100x100bb.";

    private final CoverHttpClient httpClient;

    /** Creates the provider over the shared bounded cover HTTP client. */
    public ItunesCoverArtProvider(CoverHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public CoverSource source() {
        return CoverSource.ITUNES;
    }

    @Override
    public List<CoverArtResult> search(CoverArtQuery query) throws IOException {
        List<CoverArtResult> results = new ArrayList<>();
        if (query.hasAlbum()) {
            results.addAll(parse(httpClient.getJson(albumSearchUri(query))));
        }
        results.addAll(parse(httpClient.getJson(songSearchUri(query))));
        return results;
    }

    static URI albumSearchUri(CoverArtQuery query) {
        return URI.create(API + "&entity=album&limit=5&term="
                + CoverQueries.encode(query.artist() + " " + query.album()));
    }

    static URI songSearchUri(CoverArtQuery query) {
        return URI.create(API + "&entity=song&limit=10&term="
                + CoverQueries.encode(query.artist() + " " + query.title()));
    }

    static List<CoverArtResult> parse(JsonNode root) {
        List<CoverArtResult> results = new ArrayList<>();
        for (JsonNode item : root.path("results")) {
            String artwork = item.path("artworkUrl100").asText();
            if (!artwork.contains(ARTWORK_SIZE)) {
                continue;
            }
            CoverQueries.httpsUri(artwork.replace(ARTWORK_SIZE, "/250x250bb.")).flatMap(thumb ->
                    CoverQueries.httpsUri(artwork.replace(ARTWORK_SIZE, "/600x600bb.")).map(image ->
                            new CoverArtResult(CoverSource.ITUNES,
                                    item.path("collectionName").asText(),
                                    item.path("artistName").asText(), thumb, image))).
                    ifPresent(results::add);
        }
        return results;
    }
}
