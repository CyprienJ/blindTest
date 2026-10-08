package com.blindtest.store;

import static org.assertj.core.api.Assertions.assertThat;

import com.blindtest.model.game.GameParticipantId;
import com.blindtest.model.game.RequestedGameConfiguration;
import com.blindtest.model.lobby.AudioMode;
import com.blindtest.model.lobby.LobbyCode;
import com.blindtest.model.lobby.LobbyParticipant;
import com.blindtest.model.lobby.LobbySnapshot;
import com.blindtest.model.session.SessionId;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InMemoryLobbyStoreTest {

    private static final Instant NOW = Instant.parse("2026-10-02T12:00:00Z");
    private static final Instant EXPIRY = NOW.plusSeconds(1800);

    private final InMemoryLobbyStore store = new InMemoryLobbyStore();

    @Test
    void findsByIdAndCodeAndRefusesALiveDuplicateCode() {
        LobbySnapshot lobby = lobby("ABC234");
        assertThat(store.create(lobby, NOW)).isTrue();

        assertThat(store.find(lobby.sessionId(), NOW)).contains(lobby);
        assertThat(store.findByCode(new LobbyCode("ABC234"), NOW)).contains(lobby);
        assertThat(store.create(lobby("ABC234"), NOW)).isFalse();
    }

    @Test
    void compareAndSetRequiresTheStoredVersion() {
        LobbySnapshot lobby = lobby("ABC234");
        store.create(lobby, NOW);
        LobbySnapshot joined = lobby.join(member(), NOW, EXPIRY);

        assertThat(store.compareAndSet(5, joined, NOW)).isFalse();
        assertThat(store.compareAndSet(0, joined, NOW)).isTrue();
        assertThat(store.compareAndSet(0, joined, NOW)).isFalse();
        assertThat(store.find(lobby.sessionId(), NOW).orElseThrow().version()).isEqualTo(1);
    }

    @Test
    void removalAndExpiryFreeTheCode() {
        LobbySnapshot lobby = lobby("ABC234");
        store.create(lobby, NOW);
        assertThat(store.remove(lobby.sessionId(), 3)).isFalse();
        assertThat(store.remove(lobby.sessionId(), 0)).isTrue();
        assertThat(store.findByCode(lobby.code(), NOW)).isEmpty();

        LobbySnapshot expiring = lobby("XYZ789");
        store.create(expiring, NOW);
        assertThat(store.findByCode(expiring.code(), EXPIRY)).isEmpty();
        assertThat(store.removeExpired(EXPIRY)).containsExactly(expiring);
        assertThat(store.create(lobby("XYZ789"), EXPIRY)).isFalse();
        assertThat(store.create(lobby("XYZ789"), NOW)).isTrue();
    }

    @Test
    void reopeningMovesTheCodeToTheNewLobbyAndKeepsItWhenTheStartedLobbyGoes() {
        LobbySnapshot lobby = lobby("ABC234");
        store.create(lobby, NOW);
        LobbySnapshot started = lobby.start(NOW, EXPIRY);
        store.compareAndSet(0, started, NOW);
        assertThat(store.findStarted(NOW)).containsExactly(started);
        assertThat(store.findByCode(lobby.code(), NOW)).contains(started);

        SessionId nextId = new SessionId(UUID.randomUUID());
        LobbySnapshot reopened = LobbySnapshot.open(
                nextId, lobby.code(), member(), lobby.configuration(), lobby.audioMode(), NOW, EXPIRY);
        // The started lobby must name the reopened one, at its stored version.
        assertThat(store.reopen(1, started.reopenAs(new SessionId(UUID.randomUUID()), NOW),
                reopened, NOW)).isFalse();
        assertThat(store.reopen(0, started.reopenAs(nextId, NOW), reopened, NOW)).isFalse();
        assertThat(store.reopen(1, started.reopenAs(nextId, NOW), reopened, NOW)).isTrue();

        assertThat(store.findByCode(lobby.code(), NOW)).contains(reopened);
        assertThat(store.find(lobby.sessionId(), NOW).orElseThrow().reopenedAs()).contains(nextId);
        assertThat(store.remove(lobby.sessionId(), 2)).isTrue();
        assertThat(store.findByCode(lobby.code(), NOW)).contains(reopened);
        assertThat(store.findStarted(NOW)).isEmpty();
    }

    private LobbySnapshot lobby(String code) {
        return LobbySnapshot.open(
                new SessionId(UUID.randomUUID()),
                new LobbyCode(code),
                member(),
                new RequestedGameConfiguration(UUID.randomUUID(), 5, 30, Map.of()),
                AudioMode.EVERYONE,
                NOW,
                EXPIRY);
    }

    private LobbyParticipant member() {
        return new LobbyParticipant(
                new GameParticipantId(UUID.randomUUID()), Optional.of(UUID.randomUUID()), "Player", false);
    }
}
