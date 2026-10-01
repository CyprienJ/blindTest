package com.blindtest.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.blindtest.config.properties.CoverProperties;
import com.blindtest.infra.cover.CoverHttpClient;
import com.blindtest.model.dto.CoverSuggestionRequest;
import com.blindtest.model.dto.CoverSuggestionResponse;
import com.blindtest.model.entity.CoverSource;
import com.blindtest.service.port.CoverArtProvider;
import com.blindtest.service.port.CoverArtQuery;
import com.blindtest.service.port.CoverArtResult;
import java.io.IOException;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

class CoverSuggestionServiceTest {

    private static final CoverArtQuery QUERY = new CoverArtQuery("Take On Me", "a-ha", "Hunting High and Low");

    private final CoverCandidateRegistry registry = new CoverCandidateRegistry(
            Clock.fixed(Instant.parse("2026-10-01T10:00:00Z"), ZoneOffset.UTC));
    private final CoverHttpClient httpClient = mock(CoverHttpClient.class);
    private final CoverImageNormalizer normalizer = mock(CoverImageNormalizer.class);

    @Test
    void mergeGivesEachProviderTwoPlacesInPriorityOrderThenFillsTheRest() {
        List<CoverArtResult> deezer = results(CoverSource.DEEZER, 5);
        List<CoverArtResult> itunes = results(CoverSource.ITUNES, 1);
        List<CoverArtResult> archive = results(CoverSource.COVER_ART_ARCHIVE, 3);

        List<CoverArtResult> merged = CoverSuggestionService.merge(List.of(deezer, itunes, archive), QUERY);

        assertThat(merged).containsExactly(
                deezer.get(0), deezer.get(1), itunes.get(0), archive.get(0), archive.get(1), deezer.get(2));
    }

    @Test
    void mergeRanksExactMatchesFirstAndOffersAnAlbumOnce() {
        CoverArtResult compilation = result(CoverSource.DEEZER, "Hits of the 80s", "Various", "d1");
        CoverArtResult exact = result(CoverSource.DEEZER, "Hunting High and Low", "a-ha", "d2");
        CoverArtResult sameAlbumElsewhere = result(CoverSource.ITUNES, "HUNTING HIGH AND LOW!", "A-HA", "i1");
        CoverArtResult sameImage = result(CoverSource.ITUNES, "Other", "a-ha", "d1");

        List<CoverArtResult> merged = CoverSuggestionService.merge(
                List.of(List.of(compilation, exact), List.of(sameAlbumElsewhere, sameImage)), QUERY);

        assertThat(merged).containsExactly(exact, compilation);
    }

    @Test
    void suggestIgnoresAFailingProviderAndRegistersCandidates() {
        CoverArtProvider failing = provider(CoverSource.DEEZER, null);
        CoverArtProvider itunes = provider(CoverSource.ITUNES,
                List.of(result(CoverSource.ITUNES, "Hunting High and Low", "a-ha", "i1")));
        CoverSuggestionService service = service(List.of(itunes, failing), true);

        List<CoverSuggestionResponse> suggestions = service.suggest(
                new CoverSuggestionRequest("Take On Me", "a-ha", " "));

        assertThat(suggestions).singleElement().satisfies(suggestion -> {
            assertThat(suggestion.source()).isEqualTo(CoverSource.ITUNES);
            assertThat(suggestion.album()).isEqualTo("Hunting High and Low");
            assertThat(registry.find(suggestion.candidateId())).isPresent();
        });
    }

    @Test
    void suggestReturnsNothingWhenCatalogueSearchIsDisabled() {
        CoverArtProvider deezer = provider(CoverSource.DEEZER,
                List.of(result(CoverSource.DEEZER, "Album", "a-ha", "d1")));

        assertThat(service(List.of(deezer), false).suggest(
                new CoverSuggestionRequest("Take On Me", "a-ha", null))).isEmpty();
    }

    @Test
    void uploadRegistersTheNormalizedImageAndServesItAsThumbnail() {
        byte[] jpeg = {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 1, 2, 3, 4, 5, 6, 7, 8, 9};
        byte[] png = {1, 2, 3};
        when(normalizer.normalize(png)).thenReturn(jpeg);
        CoverSuggestionService service = service(List.of(), true);

        CoverSuggestionResponse uploaded = service.upload(
                new MockMultipartFile("file", "cover.png", "image/png", png));

        assertThat(uploaded.source()).isEqualTo(CoverSource.UPLOAD);
        assertThat(service.thumbnail(uploaded.candidateId()).content()).isEqualTo(jpeg);
        assertThat(service.thumbnail(uploaded.candidateId()).contentType()).isEqualTo("image/jpeg");
    }

    @Test
    void thumbnailRejectsAProviderAnswerThatIsNotAnImage() throws IOException {
        CoverArtResult result = result(CoverSource.DEEZER, "Album", "a-ha", "d1");
        CoverCandidate candidate = registry.register(result);
        when(httpClient.downloadImage(result.thumbnailUrl())).thenReturn("<html>".getBytes());

        assertThatThrownBy(() -> service(List.of(), true).thumbnail(candidate.id())).
                isInstanceOf(ResponseStatusException.class).
                hasMessageContaining("COVER_IMAGE_UNAVAILABLE");
    }

    private CoverSuggestionService service(List<CoverArtProvider> providers, boolean enabled) {
        return new CoverSuggestionService(providers, registry, httpClient, normalizer,
                new CoverProperties(enabled));
    }

    private static CoverArtProvider provider(CoverSource source, List<CoverArtResult> results) {
        return new CoverArtProvider() {
            @Override
            public CoverSource source() {
                return source;
            }

            @Override
            public List<CoverArtResult> search(CoverArtQuery query) throws IOException {
                if (results == null) {
                    throw new IOException("unavailable");
                }
                return results;
            }
        };
    }

    private static List<CoverArtResult> results(CoverSource source, int count) {
        return IntStream.range(0, count).
                mapToObj(index -> result(source, source + " album " + index, "a-ha", source + "-" + index)).
                toList();
    }

    private static CoverArtResult result(CoverSource source, String album, String artist, String image) {
        return new CoverArtResult(source, album, artist,
                URI.create("https://cdn-images.dzcdn.net/" + image + "-thumb.jpg"),
                URI.create("https://cdn-images.dzcdn.net/" + image + ".jpg"));
    }
}
