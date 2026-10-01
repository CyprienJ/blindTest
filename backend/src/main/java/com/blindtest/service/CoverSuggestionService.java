package com.blindtest.service;

import com.blindtest.config.properties.CoverProperties;
import com.blindtest.infra.cover.CoverHttpClient;
import com.blindtest.infra.cover.CoverImageFormat;
import com.blindtest.model.dto.CoverSuggestionRequest;
import com.blindtest.model.dto.CoverSuggestionResponse;
import com.blindtest.service.port.CoverArtProvider;
import com.blindtest.service.port.CoverArtQuery;
import com.blindtest.service.port.CoverArtResult;
import java.io.IOException;
import java.net.URI;
import java.text.Normalizer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * Searches every cover catalogue in parallel and offers up to six covers.
 *
 * <p>Providers are ranked Deezer, iTunes, then Cover Art Archive. Each first contributes up to
 * {@link #FIRST_PASS_PER_SOURCE} covers in that order; remaining places are filled in the same
 * order. The same album or image is offered once, and exact album/artist matches come first
 * within a provider.
 */
@Service
@Slf4j
public class CoverSuggestionService {

    static final int MAX_SUGGESTIONS = 6;
    static final int FIRST_PASS_PER_SOURCE = 2;
    private static final Duration SEARCH_TIMEOUT = Duration.ofSeconds(8);

    private final List<CoverArtProvider> providers;
    private final CoverCandidateRegistry registry;
    private final CoverHttpClient httpClient;
    private final CoverImageNormalizer normalizer;
    private final CoverProperties properties;

    /** Creates the service with providers sorted by their source priority. */
    public CoverSuggestionService(
            List<CoverArtProvider> providers,
            CoverCandidateRegistry registry,
            CoverHttpClient httpClient,
            CoverImageNormalizer normalizer,
            CoverProperties properties) {
        this.providers = providers.stream().
                sorted(Comparator.comparing(CoverArtProvider::source)).
                toList();
        this.registry = registry;
        this.httpClient = httpClient;
        this.normalizer = normalizer;
        this.properties = properties;
    }

    /** Returns up to six cover suggestions; an unavailable provider only reduces the choice. */
    public List<CoverSuggestionResponse> suggest(CoverSuggestionRequest request) {
        if (!properties.suggestionsEnabled()) {
            return List.of();
        }
        CoverArtQuery query = new CoverArtQuery(request.title(), request.artist(), request.album());
        return merge(searchAll(query), query).stream().
                map(registry::register).
                map(CoverSuggestionService::toResponse).
                toList();
    }

    /** Validates and normalizes an uploaded image, then offers it as a candidate. */
    public CoverSuggestionResponse upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.UNSUPPORTED_MEDIA_TYPE, "COVER_IMAGE_UNSUPPORTED");
        }
        if (file.getSize() > CoverHttpClient.MAX_IMAGE_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "COVER_IMAGE_TOO_LARGE");
        }
        try {
            return toResponse(registry.registerUpload(normalizer.normalize(file.getBytes())));
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read the uploaded cover", exception);
        }
    }

    /** Returns a candidate's thumbnail bytes with their detected media type. */
    public CoverThumbnail thumbnail(UUID candidateId) {
        CoverCandidate candidate = registry.find(candidateId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (candidate.uploaded()) {
            return new CoverThumbnail(candidate.image(), "image/jpeg");
        }
        byte[] image = download(candidate.thumbnailUrl());
        return CoverImageFormat.detect(image).
                map(format -> new CoverThumbnail(image, "image/" + format.name().toLowerCase(Locale.ROOT))).
                orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY, "COVER_IMAGE_UNAVAILABLE"));
    }

    byte[] download(URI uri) {
        try {
            return httpClient.downloadImage(uri);
        } catch (IOException exception) {
            log.info("Cover image download failed: {}", exception.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "COVER_IMAGE_UNAVAILABLE");
        }
    }

    private List<List<CoverArtResult>> searchAll(CoverArtQuery query) {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<List<CoverArtResult>>> searches = providers.stream().
                    map(provider -> executor.submit(() -> provider.search(query))).
                    toList();
            long deadline = System.nanoTime() + SEARCH_TIMEOUT.toNanos();
            List<List<CoverArtResult>> results = new ArrayList<>();
            for (int index = 0; index < searches.size(); index++) {
                results.add(await(searches.get(index), providers.get(index), deadline));
            }
            return results;
        }
    }

    private List<CoverArtResult> await(
            Future<List<CoverArtResult>> search, CoverArtProvider provider, long deadline) {
        try {
            return search.get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
        } catch (ExecutionException | TimeoutException exception) {
            search.cancel(true);
            log.info("Cover search on {} failed: {}", provider.source(), exception.toString());
            return List.of();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return List.of();
        }
    }

    /** Applies the per-provider ranking, deduplication, and two-pass priority quota. */
    static List<CoverArtResult> merge(List<List<CoverArtResult>> perProvider, CoverArtQuery query) {
        List<List<CoverArtResult>> ranked = perProvider.stream().
                map(results -> results.stream().
                        sorted(Comparator.comparingInt(
                                (CoverArtResult result) -> matchScore(result, query)).reversed()).
                        toList()).
                toList();
        Set<String> seen = new HashSet<>();
        List<CoverArtResult> merged = new ArrayList<>();
        for (List<CoverArtResult> results : ranked) {
            take(results, FIRST_PASS_PER_SOURCE, seen, merged);
        }
        for (List<CoverArtResult> results : ranked) {
            take(results, MAX_SUGGESTIONS, seen, merged);
        }
        return List.copyOf(merged);
    }

    private static void take(
            List<CoverArtResult> results, int limit, Set<String> seen, List<CoverArtResult> merged) {
        int taken = 0;
        for (CoverArtResult result : results) {
            if (merged.size() >= MAX_SUGGESTIONS || taken >= limit) {
                return;
            }
            String image = result.imageUrl().toString();
            String album = normalize(result.album());
            String albumKey = album.isEmpty() ? null : album + "|" + normalize(result.artist());
            if (seen.contains(image) || albumKey != null && seen.contains(albumKey)) {
                continue;
            }
            seen.add(image);
            if (albumKey != null) {
                seen.add(albumKey);
            }
            merged.add(result);
            taken++;
        }
    }

    private static int matchScore(CoverArtResult result, CoverArtQuery query) {
        int score = 0;
        if (query.hasAlbum() && normalize(result.album()).equals(normalize(query.album()))) {
            score += 2;
        }
        if (normalize(result.artist()).equals(normalize(query.artist()))) {
            score++;
        }
        return score;
    }

    static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD).
                replaceAll("\\p{M}+", "").
                toLowerCase(Locale.ROOT).
                replaceAll("[^\\p{L}\\p{N}]+", " ").
                strip();
    }

    private static CoverSuggestionResponse toResponse(CoverCandidate candidate) {
        return new CoverSuggestionResponse(
                candidate.id(), candidate.source(), candidate.album(), candidate.artist());
    }

    /** Thumbnail bytes and their media type. */
    public record CoverThumbnail(byte[] content, String contentType) {
    }
}
