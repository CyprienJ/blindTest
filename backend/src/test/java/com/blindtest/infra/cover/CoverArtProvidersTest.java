package com.blindtest.infra.cover;

import static org.assertj.core.api.Assertions.assertThat;

import com.blindtest.model.entity.CoverSource;
import com.blindtest.service.port.CoverArtQuery;
import com.blindtest.service.port.CoverArtResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import org.junit.jupiter.api.Test;

class CoverArtProvidersTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final CoverArtQuery QUERY = new CoverArtQuery(
            "Take On Me", "a-ha", "Hunting \"High\" and Low");

    @Test
    void buildsPlainTextDeezerSearches() {
        assertThat(DeezerCoverArtProvider.albumSearchUri(QUERY)).hasToString(
                "https://api.deezer.com/search/album?limit=5&q=a-ha+Hunting+%22High%22+and+Low");
        assertThat(DeezerCoverArtProvider.trackSearchUri(QUERY)).hasToString(
                "https://api.deezer.com/search?limit=10&q=a-ha+Take+On+Me");
    }

    @Test
    void parsesDeezerAlbumsAndTracksAndSkipsUnusableCovers() throws Exception {
        assertThat(DeezerCoverArtProvider.parseAlbums(json("""
                {"data": [
                  {"title": "Hunting High and Low", "artist": {"name": "a-ha"},
                   "cover_medium": "https://cdn-images.dzcdn.net/c/250x250.jpg",
                   "cover_xl": "https://cdn-images.dzcdn.net/c/1000x1000.jpg"},
                  {"title": "No cover", "artist": {"name": "a-ha"}, "cover_medium": ""}
                ]}
                """))).containsExactly(new CoverArtResult(CoverSource.DEEZER,
                "Hunting High and Low", "a-ha",
                URI.create("https://cdn-images.dzcdn.net/c/250x250.jpg"),
                URI.create("https://cdn-images.dzcdn.net/c/1000x1000.jpg")));
        assertThat(DeezerCoverArtProvider.parseTracks(json("""
                {"data": [{"title": "Take On Me", "artist": {"name": "a-ha"},
                  "album": {"title": "Headlines", "cover_medium": "https://x.dzcdn.net/m.jpg",
                            "cover_xl": "http://x.dzcdn.net/xl.jpg"}}]}
                """))).isEmpty();
        assertThat(DeezerCoverArtProvider.parseTracks(json("{\"error\": {\"code\": 4}}"))).isEmpty();
    }

    @Test
    void parsesItunesArtworkIntoThumbnailAndLargeSizes() throws Exception {
        assertThat(ItunesCoverArtProvider.songSearchUri(QUERY)).hasToString(
                "https://itunes.apple.com/search?media=music&entity=song&limit=10&term=a-ha+Take+On+Me");
        assertThat(ItunesCoverArtProvider.parse(json("""
                {"results": [
                  {"collectionName": "Hunting High and Low", "artistName": "a-ha",
                   "artworkUrl100": "https://is1-ssl.mzstatic.com/image/thumb/a/100x100bb.jpg"},
                  {"collectionName": "Odd", "artworkUrl100": "https://is1-ssl.mzstatic.com/a.jpg"}
                ]}
                """))).singleElement().satisfies(result -> {
                    assertThat(result.source()).isEqualTo(CoverSource.ITUNES);
                    assertThat(result.thumbnailUrl()).hasToString(
                            "https://is1-ssl.mzstatic.com/image/thumb/a/250x250bb.jpg");
                    assertThat(result.imageUrl()).hasToString(
                            "https://is1-ssl.mzstatic.com/image/thumb/a/600x600bb.jpg");
                });
    }

    @Test
    void escapesMusicBrainzPhrasesAndKeepsDistinctNonCompilationReleaseGroups() throws Exception {
        assertThat(CoverArtArchiveProvider.releaseSearchUri(QUERY).getRawQuery()).contains(
                "query=release%3A%22Hunting+%5C%22High%5C%22+and+Low%22+AND+artist%3A%22a-ha%22");
        String group = "af7347df-ef5a-3c41-826d-d884f8916c17";
        assertThat(CoverArtArchiveProvider.parseRecordings(json("""
                {"recordings": [{"releases": [
                  {"title": "Hunting High and Low", "release-group": {"id": "%s"},
                   "artist-credit": [{"name": "a-ha"}]},
                  {"title": "Hunting High and Low (Deluxe)", "release-group": {"id": "%s"},
                   "artist-credit": [{"name": "a-ha"}]},
                  {"title": "80s Hits", "release-group": {"id": "6cff6e50-e0a0-473e-bd3e-5d579649694b"},
                   "artist-credit": [{"name": "Various Artists"}]},
                  {"title": "Broken", "release-group": {"id": "../../admin"},
                   "artist-credit": [{"name": "a-ha"}]}
                ]}]}
                """.formatted(group, group)))).containsExactly(
                new CoverArtArchiveProvider.ReleaseGroup(group, "Hunting High and Low", "a-ha"));
    }

    private static JsonNode json(String value) throws Exception {
        return JSON.readTree(value);
    }
}
