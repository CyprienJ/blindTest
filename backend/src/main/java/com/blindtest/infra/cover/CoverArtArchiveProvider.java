package com.blindtest.infra.cover;

import com.blindtest.model.entity.CoverSource;
import com.blindtest.service.port.CoverArtProvider;
import com.blindtest.service.port.CoverArtQuery;
import com.blindtest.service.port.CoverArtResult;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/**
 * Finds release groups through MusicBrainz, then keeps those with a front cover in the Cover
 * Art Archive.
 *
 * <p>MusicBrainz allows about one request per second per client, so searches are spaced by
 * {@link #MUSICBRAINZ_INTERVAL_NANOS}; a search that would wait too long is skipped.
 */
@Component
public class CoverArtArchiveProvider implements CoverArtProvider {

    static final int MAX_RELEASE_GROUPS = 4;
    private static final String MUSICBRAINZ = "https://musicbrainz.org/ws/2";
    private static final String COVER_ART_ARCHIVE = "https://coverartarchive.org/release-group/";
    private static final long MUSICBRAINZ_INTERVAL_NANOS = TimeUnit.MILLISECONDS.toNanos(1100);
    private static final long MAX_WAIT_NANOS = TimeUnit.SECONDS.toNanos(2);

    private final CoverHttpClient httpClient;
    private long nextMusicBrainzCall = System.nanoTime();

    /** Creates the provider over the shared bounded cover HTTP client. */
    public CoverArtArchiveProvider(CoverHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public CoverSource source() {
        return CoverSource.COVER_ART_ARCHIVE;
    }

    @Override
    public List<CoverArtResult> search(CoverArtQuery query) throws IOException {
        if (!reserveMusicBrainzCall()) {
            return List.of();
        }
        List<ReleaseGroup> groups = query.hasAlbum()
                ? parseReleases(httpClient.getJson(releaseSearchUri(query)))
                : parseRecordings(httpClient.getJson(recordingSearchUri(query)));
        List<CoverArtResult> results = new ArrayList<>();
        for (ReleaseGroup group : groups) {
            URI thumbnail = URI.create(COVER_ART_ARCHIVE + group.id() + "/front-250");
            if (httpClient.exists(thumbnail)) {
                results.add(new CoverArtResult(CoverSource.COVER_ART_ARCHIVE, group.title(),
                        group.artist(), thumbnail,
                        URI.create(COVER_ART_ARCHIVE + group.id() + "/front-500")));
            }
        }
        return results;
    }

    static URI releaseSearchUri(CoverArtQuery query) {
        return URI.create(MUSICBRAINZ + "/release/?fmt=json&limit=10&query=" + CoverQueries.encode(
                "release:" + CoverQueries.lucenePhrase(query.album())
                        + " AND artist:" + CoverQueries.lucenePhrase(query.artist())));
    }

    static URI recordingSearchUri(CoverArtQuery query) {
        return URI.create(MUSICBRAINZ + "/recording/?fmt=json&limit=5&query=" + CoverQueries.encode(
                "recording:" + CoverQueries.lucenePhrase(query.title())
                        + " AND artist:" + CoverQueries.lucenePhrase(query.artist())));
    }

    static List<ReleaseGroup> parseReleases(JsonNode root) {
        Map<String, ReleaseGroup> groups = new LinkedHashMap<>();
        for (JsonNode release : root.path("releases")) {
            addGroup(groups, release);
        }
        return List.copyOf(groups.values());
    }

    static List<ReleaseGroup> parseRecordings(JsonNode root) {
        Map<String, ReleaseGroup> groups = new LinkedHashMap<>();
        for (JsonNode recording : root.path("recordings")) {
            for (JsonNode release : recording.path("releases")) {
                addGroup(groups, release);
            }
        }
        return List.copyOf(groups.values());
    }

    private static void addGroup(Map<String, ReleaseGroup> groups, JsonNode release) {
        String id = release.path("release-group").path("id").asText();
        String artist = release.path("artist-credit").path(0).path("name").asText();
        if (groups.size() >= MAX_RELEASE_GROUPS || !id.matches("[0-9a-f-]{36}")
                || "Various Artists".equalsIgnoreCase(artist)) {
            return;
        }
        groups.putIfAbsent(id, new ReleaseGroup(id, release.path("title").asText(), artist));
    }

    /** Reserves the next MusicBrainz slot, then waits for it outside the lock. */
    private boolean reserveMusicBrainzCall() {
        long wait;
        synchronized (this) {
            long now = System.nanoTime();
            wait = nextMusicBrainzCall - now;
            if (wait > MAX_WAIT_NANOS) {
                return false;
            }
            nextMusicBrainzCall = Math.max(now, nextMusicBrainzCall) + MUSICBRAINZ_INTERVAL_NANOS;
        }
        if (wait > 0) {
            try {
                TimeUnit.NANOSECONDS.sleep(wait);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return true;
    }

    /** One MusicBrainz release group, which shares its front cover across editions. */
    record ReleaseGroup(String id, String title, String artist) {
    }
}
