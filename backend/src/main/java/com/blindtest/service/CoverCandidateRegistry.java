package com.blindtest.service;

import com.blindtest.model.entity.CoverSource;
import com.blindtest.service.port.CoverArtResult;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Bounded, expiring in-memory registry of cover candidates offered to administrators. */
@Component
public class CoverCandidateRegistry {

    static final Duration TTL = Duration.ofMinutes(30);
    static final int MAX_CANDIDATES = 500;
    static final int MAX_UPLOADS = 10;

    private final Clock clock;
    private final Map<UUID, CoverCandidate> candidates = new LinkedHashMap<>();

    /** Creates an empty registry using the application clock for expiry. */
    public CoverCandidateRegistry(Clock clock) {
        this.clock = clock;
    }

    synchronized CoverCandidate register(CoverArtResult result) {
        return add(new CoverCandidate(UUID.randomUUID(), result.source(), result.album(),
                result.artist(), result.thumbnailUrl(), result.imageUrl(), null, expiry()));
    }

    synchronized CoverCandidate registerUpload(byte[] normalizedJpeg) {
        long uploads = candidates.values().stream().filter(CoverCandidate::uploaded).count();
        if (uploads >= MAX_UPLOADS) {
            candidates.values().stream().filter(CoverCandidate::uploaded).findFirst().
                    ifPresent(oldest -> candidates.remove(oldest.id()));
        }
        return add(new CoverCandidate(UUID.randomUUID(), CoverSource.UPLOAD, "", "", null, null,
                normalizedJpeg.clone(), expiry()));
    }

    synchronized Optional<CoverCandidate> find(UUID id) {
        evictExpired();
        return Optional.ofNullable(candidates.get(id));
    }

    private CoverCandidate add(CoverCandidate candidate) {
        evictExpired();
        Iterator<UUID> oldest = candidates.keySet().iterator();
        while (candidates.size() >= MAX_CANDIDATES && oldest.hasNext()) {
            oldest.next();
            oldest.remove();
        }
        candidates.put(candidate.id(), candidate);
        return candidate;
    }

    private void evictExpired() {
        Instant now = clock.instant();
        candidates.values().removeIf(candidate -> !candidate.expiresAt().isAfter(now));
    }

    private Instant expiry() {
        return clock.instant().plus(TTL);
    }
}
