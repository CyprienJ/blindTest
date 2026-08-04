package com.blindtest.store;

import com.blindtest.model.session.ActiveSessionSnapshot;
import com.blindtest.model.session.SessionId;
import java.time.Instant;
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

    int removeExpired(Instant now);

    int activeCount(Instant now);
}
