package com.blindtest.model.lobby;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.blindtest.model.game.GameParticipantId;
import com.blindtest.model.game.RequestedGameConfiguration;
import com.blindtest.model.session.SessionId;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LobbySnapshotTest {

    private static final Instant NOW = Instant.parse("2026-10-02T12:00:00Z");
    private static final Instant LATER = NOW.plusSeconds(1800);

    @Test
    void parsesCodesCaseInsensitivelyAndRejectsAmbiguousCharacters() {
        assertThat(LobbyCode.parse(" abc234 ")).contains(new LobbyCode("ABC234"));
        assertThat(LobbyCode.parse("ABC23O")).isEmpty();
        assertThat(LobbyCode.parse("ABC231")).isEmpty();
        assertThat(LobbyCode.parse("ABCDE")).isEmpty();
        assertThat(LobbyCode.parse(null)).isEmpty();
        assertThat(LobbyCode.ALPHABET).doesNotContain("0", "O", "1", "I");
    }

    @Test
    void startsOnlyWithTwoMembersWhenEveryNonHostIsReady() {
        LobbyParticipant host = member("Host", true);
        LobbySnapshot lobby = open(host);
        assertThat(lobby.canStart()).isFalse();

        LobbyParticipant guest = member("Guest", true);
        LobbySnapshot joined = lobby.join(guest, NOW, LATER);
        assertThat(joined.canStart()).isFalse();
        assertThat(joined.version()).isEqualTo(1);

        LobbySnapshot ready = joined.withReady(guest.participantId(), true, NOW, LATER);
        assertThat(ready.canStart()).isTrue();
        // The host's own flag never blocks the start.
        assertThat(ready.withReady(host.participantId(), false, NOW, LATER).canStart()).isTrue();
    }

    @Test
    void hostDepartureMovesTheRoleToTheEarliestRegisteredMember() {
        LobbyParticipant host = member("Host", true);
        LobbyParticipant guest = member("Guest", false);
        LobbyParticipant second = member("Second", true);
        LobbySnapshot lobby = open(host).join(guest, NOW, LATER).join(second, NOW, LATER);

        LobbySnapshot afterHostLeft = lobby.leave(host.participantId(), NOW, LATER).orElseThrow();
        assertThat(afterHostLeft.hostParticipantId()).isEqualTo(second.participantId());
        assertThat(afterHostLeft.participants()).extracting(LobbyParticipant::displayName).
                containsExactly("Guest", "Second");

        LobbySnapshot memberLeft = lobby.leave(guest.participantId(), NOW, LATER).orElseThrow();
        assertThat(memberLeft.hostParticipantId()).isEqualTo(host.participantId());
        assertThat(open(host).leave(host.participantId(), NOW, LATER)).isEmpty();
    }

    @Test
    void rejoinedMembersStartUnreadyAndTheLobbyCapsAtTenAccounts() {
        LobbySnapshot lobby = open(member("Host", true));
        for (int index = 1; index < 10; index++) {
            lobby = lobby.join(member("Player " + index, true), NOW, LATER);
        }
        assertThat(lobby.isFull()).isTrue();
        LobbySnapshot full = lobby;
        assertThatThrownBy(() -> full.join(member("Eleventh", true), NOW, LATER)).
                hasMessage("a lobby holds one to ten participants");

        LobbyParticipant duplicate = full.participants().get(1);
        LobbySnapshot nine = full.leave(duplicate.participantId(), NOW, LATER).orElseThrow();
        assertThatThrownBy(() -> nine.join(
                        new LobbyParticipant(
                                new GameParticipantId(UUID.randomUUID()),
                                full.participants().get(2).accountId(),
                                "Same account",
                                true),
                        NOW, LATER)).
                hasMessage("participant accounts must be unique");
        assertThat(nine.join(member("Late", true).withReady(true), NOW, LATER).
                participants().getLast().ready()).isFalse();
    }

    @Test
    void expiresAtTheInclusiveInstant() {
        LobbySnapshot lobby = open(member("Host", true));
        assertThat(lobby.isExpiredAt(LATER.minusNanos(1))).isFalse();
        assertThat(lobby.isExpiredAt(LATER)).isTrue();
    }

    static LobbySnapshot open(LobbyParticipant host) {
        return LobbySnapshot.open(
                new SessionId(UUID.randomUUID()),
                new LobbyCode("ABC234"),
                host,
                new RequestedGameConfiguration(UUID.randomUUID(), 5, 30, Map.of()),
                AudioMode.EVERYONE,
                NOW,
                LATER);
    }

    static LobbyParticipant member(String name, boolean registered) {
        return new LobbyParticipant(
                new GameParticipantId(UUID.randomUUID()),
                registered ? Optional.of(UUID.randomUUID()) : Optional.empty(),
                name,
                false);
    }
}
