package com.blindtest.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.blindtest.model.entity.CoverSource;
import com.blindtest.service.port.CoverArtResult;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CoverCandidateRegistryTest {

    private Instant now = Instant.parse("2026-10-01T10:00:00Z");
    private final Clock clock = new Clock() {
        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    };
    private final CoverCandidateRegistry registry = new CoverCandidateRegistry(clock);

    @Test
    void candidatesExpireAfterTheirTimeToLive() {
        CoverCandidate candidate = registry.register(result());

        now = now.plus(CoverCandidateRegistry.TTL).minusSeconds(1);
        assertThat(registry.find(candidate.id())).contains(candidate);
        now = now.plusSeconds(1);
        assertThat(registry.find(candidate.id())).isEmpty();
    }

    @Test
    void keepsOnlyTheMostRecentUploadsAndCandidates() {
        List<CoverCandidate> uploads = new ArrayList<>();
        for (int index = 0; index <= CoverCandidateRegistry.MAX_UPLOADS; index++) {
            uploads.add(registry.registerUpload(new byte[] {(byte) index}));
        }
        assertThat(registry.find(uploads.getFirst().id())).isEmpty();
        assertThat(registry.find(uploads.getLast().id())).isPresent();

        CoverCandidate first = registry.register(result());
        for (int index = 0; index < CoverCandidateRegistry.MAX_CANDIDATES; index++) {
            registry.register(result());
        }
        assertThat(registry.find(first.id())).isEmpty();
    }

    private static CoverArtResult result() {
        return new CoverArtResult(CoverSource.DEEZER, "Album", "Artist",
                URI.create("https://cdn-images.dzcdn.net/t.jpg"),
                URI.create("https://cdn-images.dzcdn.net/i.jpg"));
    }
}
