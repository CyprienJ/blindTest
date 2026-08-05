package com.blindtest.store;

import com.blindtest.model.session.ActiveSessionSnapshot;
import com.blindtest.model.session.SessionId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/** Process-local session adapter with atomic per-session compare-and-set operations. */
public final class InMemorySessionStore implements SessionStore {

    private final ConcurrentHashMap<SessionId, ActiveSessionSnapshot> sessions =
            new ConcurrentHashMap<>();

    @Override
    public SessionCreateOutcome create(ActiveSessionSnapshot session, Instant now) {
        Objects.requireNonNull(session, "session is required");
        requireNow(now);
        requireActive(session, now);
        AtomicReference<SessionCreateOutcome> outcome = new AtomicReference<>();
        sessions.compute(session.sessionId(), (sessionId, existing) -> {
            if (existing == null) {
                outcome.set(SessionCreateOutcome.CREATED);
                return session;
            }
            outcome.set(SessionCreateOutcome.ALREADY_EXISTS);
            return existing;
        });
        return outcome.get();
    }

    @Override
    public Optional<ActiveSessionSnapshot> find(SessionId sessionId, Instant now) {
        Objects.requireNonNull(sessionId, "sessionId is required");
        requireNow(now);
        ActiveSessionSnapshot found = sessions.get(sessionId);
        return found == null || found.isExpiredAt(now) ? Optional.empty() : Optional.of(found);
    }

    @Override
    public SessionUpdateOutcome compareAndSet(
            SessionId sessionId,
            long expectedVersion,
            ActiveSessionSnapshot replacement,
            Instant now) {
        Objects.requireNonNull(sessionId, "sessionId is required");
        Objects.requireNonNull(replacement, "replacement is required");
        requireNow(now);
        validateReplacementIdentity(sessionId, expectedVersion, replacement);
        requireActive(replacement, now);
        AtomicReference<SessionUpdateOutcome> outcome =
                new AtomicReference<>(SessionUpdateOutcome.NOT_FOUND);
        sessions.compute(sessionId, (ignored, existing) -> {
            if (existing == null || existing.isExpiredAt(now)) {
                return existing;
            }
            if (existing.version() != expectedVersion) {
                outcome.set(SessionUpdateOutcome.VERSION_CONFLICT);
                return existing;
            }
            validateStableMetadata(existing, replacement);
            outcome.set(SessionUpdateOutcome.UPDATED);
            return replacement;
        });
        return outcome.get();
    }

    @Override
    public SessionRemoveOutcome remove(
            SessionId sessionId,
            long expectedVersion,
            Instant now) {
        Objects.requireNonNull(sessionId, "sessionId is required");
        requireNow(now);
        AtomicReference<SessionRemoveOutcome> outcome =
                new AtomicReference<>(SessionRemoveOutcome.NOT_FOUND);
        sessions.compute(sessionId, (ignored, existing) -> {
            if (existing == null) {
                return null;
            }
            if (existing.version() != expectedVersion) {
                outcome.set(SessionRemoveOutcome.VERSION_CONFLICT);
                return existing;
            }
            outcome.set(SessionRemoveOutcome.REMOVED);
            return null;
        });
        return outcome.get();
    }

    @Override
    public List<ActiveSessionSnapshot> removeExpiredSessions(Instant now) {
        requireNow(now);
        List<ActiveSessionSnapshot> removed = new ArrayList<>();
        sessions.forEach((sessionId, observed) -> sessions.computeIfPresent(
                sessionId, (ignored, current) -> {
                    if (current.isExpiredAt(now)) {
                        removed.add(current);
                        return null;
                    }
                    return current;
                }));
        return List.copyOf(removed);
    }

    @Override
    public int activeCount(Instant now) {
        requireNow(now);
        return Math.toIntExact(sessions.values().stream().
                filter(session -> !session.isExpiredAt(now)).count());
    }

    private void validateReplacementIdentity(
            SessionId sessionId,
            long expectedVersion,
            ActiveSessionSnapshot replacement) {
        if (expectedVersion < 0) {
            throw new IllegalArgumentException("expectedVersion cannot be negative");
        }
        if (!replacement.sessionId().equals(sessionId)) {
            throw new IllegalArgumentException("replacement sessionId must match");
        }
        if (replacement.version() != Math.addExact(expectedVersion, 1)) {
            throw new IllegalArgumentException("replacement must increment version exactly once");
        }
    }

    private void validateStableMetadata(
            ActiveSessionSnapshot existing,
            ActiveSessionSnapshot replacement) {
        if (!replacement.createdAt().equals(existing.createdAt())
                || !replacement.ownerParticipantId().equals(existing.ownerParticipantId())
                || !replacement.configuration().equals(existing.configuration())
                || replacement.updatedAt().isBefore(existing.updatedAt())) {
            throw new IllegalArgumentException("replacement changes stable session metadata");
        }
    }

    private void requireActive(ActiveSessionSnapshot session, Instant now) {
        if (session.isExpiredAt(now)) {
            throw new IllegalArgumentException("session must not already be expired");
        }
    }

    private void requireNow(Instant now) {
        Objects.requireNonNull(now, "now is required");
    }
}
