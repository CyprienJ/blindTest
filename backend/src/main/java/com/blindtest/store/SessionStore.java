package com.blindtest.store;

import com.blindtest.model.session.ActiveSessionSnapshot;
import com.blindtest.model.session.SessionId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Replaceable port for canonical active-session state and optimistic versioning. */
public interface SessionStore {

    SessionCreateOutcome create(ActiveSessionSnapshot session, Instant now);

    Optional<ActiveSessionSnapshot> find(SessionId sessionId, Instant now);

    SessionUpdateOutcome compareAndSet(
            SessionId sessionId,
            long expectedVersion,
            ActiveSessionSnapshot replacement,
            Instant now);

    SessionRemoveOutcome remove(SessionId sessionId, long expectedVersion, Instant now);

    /** Atomically removes and returns snapshots expired at the inclusive instant. */
    List<ActiveSessionSnapshot> removeExpiredSessions(Instant now);

    default int removeExpired(Instant now) {
        return removeExpiredSessions(now).size();
    }

    int activeCount(Instant now);
}
