package com.blindtest.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.blindtest.model.game.GameParticipantId;
import com.blindtest.model.game.SecondaryDiscoveryState;
import com.blindtest.model.session.ActiveSessionSnapshot;
import com.blindtest.model.session.SessionParticipant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ActiveSessionSnapshotTest {

    @Test
    void copiesParticipantsAndCreatesExactlyOneNextVersion() {
        List<SessionParticipant> participants = new ArrayList<>(
                List.of(SessionTestFixtures.owner()));
        ActiveSessionSnapshot initial = SessionTestFixtures.session(participants);
        participants.clear();

        ActiveSessionSnapshot next = initial.next(
                initial.gameState(),
                SecondaryDiscoveryState.empty(),
                SessionTestFixtures.NOW.plusSeconds(1),
                SessionTestFixtures.NOW.plus(Duration.ofMinutes(31)));

        assertThat(initial.participants()).containsExactly(SessionTestFixtures.owner());
        assertThatThrownBy(() -> initial.participants().clear()).
                isInstanceOf(UnsupportedOperationException.class);
        assertThat(initial.version()).isZero();
        assertThat(next.version()).isOne();
        assertThat(next.sessionId()).isEqualTo(initial.sessionId());
        assertThat(next.ownerParticipantId()).isEqualTo(initial.ownerParticipantId());
    }

    @Test
    void validatesOwnerParticipantConfigurationVersionAndExpiry() {
        ActiveSessionSnapshot valid = SessionTestFixtures.session();
        GameParticipantId absentOwner = new GameParticipantId(
                UUID.fromString("00000000-0000-0000-0000-000000000099"));

        assertThatThrownBy(() -> SessionTestFixtures.copy(
                        valid,
                        absentOwner,
                        valid.participants(),
                        valid.version(),
                        valid.updatedAt(),
                        valid.expiresAt())).
                isInstanceOf(IllegalArgumentException.class).
                hasMessage("owner must be an active participant");
        assertThatThrownBy(() -> SessionTestFixtures.copy(
                        valid,
                        valid.ownerParticipantId(),
                        valid.participants(),
                        -1,
                        valid.updatedAt(),
                        valid.expiresAt())).
                isInstanceOf(IllegalArgumentException.class).
                hasMessage("version cannot be negative");
        assertThatThrownBy(() -> SessionTestFixtures.copy(
                        valid,
                        valid.ownerParticipantId(),
                        valid.participants(),
                        valid.version(),
                        valid.updatedAt(),
                        valid.updatedAt())).
                isInstanceOf(IllegalArgumentException.class).
                hasMessage("expiresAt must follow updatedAt");
    }

    @Test
    void rejectsDuplicateParticipantAndAccountIdentities() {
        SessionParticipant owner = SessionTestFixtures.owner();
        SessionParticipant duplicateParticipant = new SessionParticipant(
                owner.participantId(), Optional.empty(), "Guest");
        SessionParticipant duplicateAccount = new SessionParticipant(
                new GameParticipantId(
                        UUID.fromString("00000000-0000-0000-0000-000000000003")),
                owner.accountId(),
                "Same account");

        assertThatThrownBy(() -> SessionTestFixtures.session(
                        List.of(owner, duplicateParticipant))).
                isInstanceOf(IllegalArgumentException.class).
                hasMessage("participant identities must be unique");
        assertThatThrownBy(() -> SessionTestFixtures.session(
                        List.of(owner, duplicateAccount))).
                isInstanceOf(IllegalArgumentException.class).
                hasMessage("participant accounts must be unique");
    }

    @Test
    void expiryIsInclusiveAtTheAuthoritativeInstant() {
        ActiveSessionSnapshot session = SessionTestFixtures.session();

        assertThat(session.isExpiredAt(session.expiresAt().minusNanos(1))).isFalse();
        assertThat(session.isExpiredAt(session.expiresAt())).isTrue();
        assertThat(session.isExpiredAt(session.expiresAt().plusNanos(1))).isTrue();
    }
}
