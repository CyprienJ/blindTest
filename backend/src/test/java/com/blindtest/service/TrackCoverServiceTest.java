package com.blindtest.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.blindtest.config.properties.ImportProperties;
import com.blindtest.model.entity.CoverSource;
import com.blindtest.model.entity.TrackEntity;
import com.blindtest.service.port.CoverArtResult;
import com.blindtest.testing.InMemoryMediaStorage;
import java.net.URI;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.server.ResponseStatusException;

class TrackCoverServiceTest {

    private static final byte[] JPEG = {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0, 1, 2, 3, 4, 5, 6, 7, 8};

    @TempDir
    private Path tempDir;

    private final CoverCandidateRegistry registry = new CoverCandidateRegistry(
            Clock.fixed(Instant.parse("2026-10-01T10:00:00Z"), ZoneOffset.UTC));
    private final CoverSuggestionService suggestionService = mock(CoverSuggestionService.class);
    private final CoverImageNormalizer normalizer = mock(CoverImageNormalizer.class);
    private final TrackMediaService trackMediaService = mock(TrackMediaService.class);
    private final InMemoryMediaStorage storage = new InMemoryMediaStorage();
    private TrackCoverService service;
    private TrackEntity track;

    @BeforeEach
    void setUp() {
        service = new TrackCoverService(registry, suggestionService, normalizer, storage,
                trackMediaService, new ImportProperties(tempDir, 1024, Duration.ofMinutes(1), null));
        track = new TrackEntity();
    }

    @Test
    void downloadsNormalizesAndStoresAProviderCoverUnderANewId() {
        URI image = URI.create("https://cdn-images.dzcdn.net/cover.jpg");
        CoverCandidate candidate = registry.register(new CoverArtResult(CoverSource.DEEZER,
                "Album", "Artist", URI.create("https://cdn-images.dzcdn.net/thumb.jpg"), image));
        byte[] downloaded = {9, 9, 9};
        when(suggestionService.download(image)).thenReturn(downloaded);
        when(normalizer.normalize(downloaded)).thenReturn(JPEG);

        service.replaceCover(track, candidate.id());

        assertThat(track.getCoverSource()).isEqualTo(CoverSource.DEEZER);
        String key = "covers/" + track.getCoverId() + ".jpg";
        assertThat(storage.content(key)).isEqualTo(JPEG);
        assertThat(storage.contentType(key)).isEqualTo("image/jpeg");
        assertThat(service.read(track.getCoverId())).hasValueSatisfying(
                content -> assertThat(content).isEqualTo(JPEG));
        assertThat(tempDir.resolve("covers")).isEmptyDirectory();
    }

    @Test
    void replacingQueuesThePreviousCoverAndUploadsNeedNoDownload() {
        UUID previous = UUID.randomUUID();
        track.setCoverId(previous);
        track.setCoverSource(CoverSource.ITUNES);
        CoverCandidate upload = registry.registerUpload(JPEG);

        service.replaceCover(track, upload.id());

        verify(trackMediaService).enqueueDeletion("covers/" + previous + ".jpg");
        verifyNoInteractions(suggestionService, normalizer);
        assertThat(track.getCoverId()).isNotEqualTo(previous);
        assertThat(track.getCoverSource()).isEqualTo(CoverSource.UPLOAD);
    }

    @Test
    void rejectsAnUnknownOrExpiredCandidate() {
        assertThatThrownBy(() -> service.replaceCover(track, UUID.randomUUID())).
                isInstanceOf(ResponseStatusException.class).
                hasMessageContaining("COVER_SUGGESTION_EXPIRED");
        assertThat(storage.listObjectKeys()).isEmpty();
    }

    @Test
    void removingClearsTheCoverAndQueuesItsObject() {
        UUID coverId = UUID.randomUUID();
        track.setCoverId(coverId);
        track.setCoverSource(CoverSource.UPLOAD);

        service.removeCover(track);

        assertThat(track.getCoverId()).isNull();
        assertThat(track.getCoverSource()).isNull();
        verify(trackMediaService).enqueueDeletion("covers/" + coverId + ".jpg");
    }
}
