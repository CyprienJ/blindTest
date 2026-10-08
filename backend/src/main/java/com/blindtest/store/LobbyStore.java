package com.blindtest.store;

import com.blindtest.model.lobby.LobbyCode;
import com.blindtest.model.lobby.LobbySnapshot;
import com.blindtest.model.session.SessionId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Replaceable port for live multiplayer lobbies indexed by session ID and join code. */
public interface LobbyStore {

    /** Stores a new lobby unless its session ID or join code is already live. */
    boolean create(LobbySnapshot lobby, Instant now);

    Optional<LobbySnapshot> find(SessionId sessionId, Instant now);

    Optional<LobbySnapshot> findByCode(LobbyCode code, Instant now);

    /** Replaces the lobby only when its stored version equals {@code expectedVersion}. */
    boolean compareAndSet(long expectedVersion, LobbySnapshot replacement, Instant now);

    /**
     * Atomically replaces a started lobby, when its stored version equals
     * {@code expectedVersion}, and stores the lobby reopened under a new session ID. The join code
     * moves to the reopened lobby.
     */
    boolean reopen(long expectedVersion, LobbySnapshot startedReplacement, LobbySnapshot reopened,
            Instant now);

    /** Removes the lobby and frees its code unless a reopened lobby now holds the code. */
    boolean remove(SessionId sessionId, long expectedVersion);

    /** Returns the live lobbies whose game has started. */
    List<LobbySnapshot> findStarted(Instant now);

    /** Atomically removes and returns lobbies expired at the inclusive instant. */
    List<LobbySnapshot> removeExpired(Instant now);
}
