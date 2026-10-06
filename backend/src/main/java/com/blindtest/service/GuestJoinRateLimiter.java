package com.blindtest.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Fixed-window limits on guest join attempts per client address and per lobby code (ADR-0020).
 * Attempts count before Turnstile is checked, so a burst cannot spend verification calls either.
 */
@Component
public class GuestJoinRateLimiter {

    static final Duration WINDOW = Duration.ofMinutes(10);
    // Players in one room usually share a public address, so this stays above a full lobby.
    static final int MAXIMUM_PER_CLIENT = 30;
    static final int MAXIMUM_PER_CODE = 30;

    private final Clock clock;
    private final Map<String, Window> clients = new ConcurrentHashMap<>();
    private final Map<String, Window> codes = new ConcurrentHashMap<>();

    /** Wires the application clock. */
    public GuestJoinRateLimiter(Clock clock) {
        this.clock = clock;
    }

    /** Records one attempt and returns whether both the client and the code are within limits. */
    public boolean tryAcquire(String clientAddress, String code) {
        Instant now = clock.instant();
        boolean clientAllowed = count(clients, clientAddress, now) <= MAXIMUM_PER_CLIENT;
        boolean codeAllowed = count(codes, code, now) <= MAXIMUM_PER_CODE;
        return clientAllowed && codeAllowed;
    }

    /** Drops windows that ended so abandoned addresses and codes do not accumulate. */
    @Scheduled(fixedDelayString = "${app.session.cleanup-delay:PT1M}")
    public void removeExpired() {
        Instant now = clock.instant();
        clients.values().removeIf(window -> window.endedAt(now));
        codes.values().removeIf(window -> window.endedAt(now));
    }

    private static int count(Map<String, Window> windows, String key, Instant now) {
        return windows.compute(key, (ignored, window) ->
                window == null || window.endedAt(now)
                        ? new Window(now, 1)
                        : new Window(window.startedAt(), window.attempts() + 1)).
                attempts();
    }

    private record Window(Instant startedAt, int attempts) {

        boolean endedAt(Instant now) {
            return !startedAt.plus(WINDOW).isAfter(now);
        }
    }
}
