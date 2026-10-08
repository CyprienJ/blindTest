package com.blindtest.store;

import com.blindtest.model.lobby.LobbyCode;
import com.blindtest.model.lobby.LobbySnapshot;
import com.blindtest.model.session.SessionId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Process-local lobby adapter; one monitor keeps the ID and code indexes consistent. */
public final class InMemoryLobbyStore implements LobbyStore {

    private final Map<SessionId, LobbySnapshot> lobbies = new HashMap<>();
    private final Map<LobbyCode, SessionId> codes = new HashMap<>();

    @Override
    public synchronized boolean create(LobbySnapshot lobby, Instant now) {
        Objects.requireNonNull(lobby, "lobby is required");
        Objects.requireNonNull(now, "now is required");
        removeExpired(now);
        if (lobbies.containsKey(lobby.sessionId()) || codes.containsKey(lobby.code())
                || lobby.isExpiredAt(now)) {
            return false;
        }
        lobbies.put(lobby.sessionId(), lobby);
        codes.put(lobby.code(), lobby.sessionId());
        return true;
    }

    @Override
    public synchronized Optional<LobbySnapshot> find(SessionId sessionId, Instant now) {
        LobbySnapshot found = lobbies.get(Objects.requireNonNull(sessionId, "sessionId is required"));
        return found == null || found.isExpiredAt(now) ? Optional.empty() : Optional.of(found);
    }

    @Override
    public synchronized Optional<LobbySnapshot> findByCode(LobbyCode code, Instant now) {
        SessionId sessionId = codes.get(Objects.requireNonNull(code, "code is required"));
        return sessionId == null ? Optional.empty() : find(sessionId, now);
    }

    @Override
    public synchronized boolean compareAndSet(
            long expectedVersion,
            LobbySnapshot replacement,
            Instant now) {
        Objects.requireNonNull(replacement, "replacement is required");
        LobbySnapshot existing = lobbies.get(replacement.sessionId());
        if (existing == null || existing.isExpiredAt(now) || existing.version() != expectedVersion
                || replacement.version() != expectedVersion + 1
                || !existing.code().equals(replacement.code())) {
            return false;
        }
        lobbies.put(replacement.sessionId(), replacement);
        return true;
    }

    @Override
    public synchronized boolean reopen(
            long expectedVersion,
            LobbySnapshot startedReplacement,
            LobbySnapshot reopened,
            Instant now) {
        Objects.requireNonNull(startedReplacement, "startedReplacement is required");
        Objects.requireNonNull(reopened, "reopened is required");
        if (!startedReplacement.started() || reopened.started()
                || !startedReplacement.code().equals(reopened.code())
                || !startedReplacement.reopenedAs().equals(Optional.of(reopened.sessionId()))
                || lobbies.containsKey(reopened.sessionId()) || reopened.isExpiredAt(now)
                || !compareAndSet(expectedVersion, startedReplacement, now)) {
            return false;
        }
        lobbies.put(reopened.sessionId(), reopened);
        codes.put(reopened.code(), reopened.sessionId());
        return true;
    }

    @Override
    public synchronized boolean remove(SessionId sessionId, long expectedVersion) {
        LobbySnapshot existing = lobbies.get(Objects.requireNonNull(sessionId, "sessionId is required"));
        if (existing == null || existing.version() != expectedVersion) {
            return false;
        }
        lobbies.remove(sessionId);
        codes.remove(existing.code(), sessionId);
        return true;
    }

    @Override
    public synchronized List<LobbySnapshot> findStarted(Instant now) {
        return lobbies.values().stream().
                filter(lobby -> lobby.started() && !lobby.isExpiredAt(now)).
                toList();
    }

    @Override
    public synchronized List<LobbySnapshot> removeExpired(Instant now) {
        Objects.requireNonNull(now, "now is required");
        List<LobbySnapshot> expired = new ArrayList<>();
        lobbies.values().removeIf(lobby -> {
            if (lobby.isExpiredAt(now)) {
                expired.add(lobby);
                return true;
            }
            return false;
        });
        expired.forEach(lobby -> codes.remove(lobby.code(), lobby.sessionId()));
        return expired;
    }
}
