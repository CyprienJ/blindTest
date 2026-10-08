package com.blindtest.model.lobby;

import com.blindtest.model.game.GameParticipantId;
import com.blindtest.model.game.GameStateSnapshot;
import com.blindtest.model.game.RequestedGameConfiguration;
import com.blindtest.model.session.SessionId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Immutable multiplayer lobby (ADR-0019). Tracks are snapshotted only when the host starts, so
 * the lobby keeps the requested configuration. Its session ID becomes the game session's ID at
 * start; the started lobby then only remembers the roster, settings, and code so that players
 * can return to a reopened lobby under a new session ID after the game (ADR-0023).
 *
 * @param started whether the game began; a started lobby accepts no member operations
 * @param reopenedAs the lobby reopened after this started lobby's game, if any
 */
public record LobbySnapshot(
        SessionId sessionId,
        LobbyCode code,
        GameParticipantId hostParticipantId,
        List<LobbyParticipant> participants,
        RequestedGameConfiguration configuration,
        AudioMode audioMode,
        long version,
        Instant createdAt,
        Instant updatedAt,
        Instant expiresAt,
        boolean started,
        Optional<SessionId> reopenedAs) {

    /** Minimum players, host included, before the host may start. */
    public static final int MINIMUM_PARTICIPANTS = 2;

    /** Copies participants and enforces membership, host, version, and expiry invariants. */
    public LobbySnapshot {
        Objects.requireNonNull(sessionId, "sessionId is required");
        Objects.requireNonNull(code, "code is required");
        Objects.requireNonNull(hostParticipantId, "hostParticipantId is required");
        Objects.requireNonNull(participants, "participants are required");
        Objects.requireNonNull(configuration, "configuration is required");
        Objects.requireNonNull(audioMode, "audioMode is required");
        Objects.requireNonNull(createdAt, "createdAt is required");
        Objects.requireNonNull(updatedAt, "updatedAt is required");
        Objects.requireNonNull(expiresAt, "expiresAt is required");
        Objects.requireNonNull(reopenedAs, "reopenedAs is required");
        participants = List.copyOf(participants);
        if (participants.isEmpty() || participants.size() > GameStateSnapshot.MAXIMUM_PARTICIPANTS) {
            throw new IllegalArgumentException("a lobby holds one to ten participants");
        }
        requireUnique(participants);
        if (participants.stream().noneMatch(member -> member.participantId().equals(hostParticipantId))) {
            throw new IllegalArgumentException("host must be a participant");
        }
        if (version < 0 || updatedAt.isBefore(createdAt) || !expiresAt.isAfter(updatedAt)) {
            throw new IllegalArgumentException("lobby version or timestamps are invalid");
        }
        if (reopenedAs.isPresent() && (!started || reopenedAs.get().equals(sessionId))) {
            throw new IllegalArgumentException("only a started lobby reopens under a new session");
        }
    }

    /** Creates a lobby holding only its host. */
    public static LobbySnapshot open(
            SessionId sessionId,
            LobbyCode code,
            LobbyParticipant host,
            RequestedGameConfiguration configuration,
            AudioMode audioMode,
            Instant now,
            Instant expiresAt) {
        return new LobbySnapshot(
                sessionId, code, host.participantId(), List.of(host), configuration, audioMode,
                0, now, now, expiresAt, false, Optional.empty());
    }

    /** Returns whether the lobby has expired at the inclusive instant. */
    public boolean isExpiredAt(Instant instant) {
        return !expiresAt.isAfter(instant);
    }

    /** Returns whether no further participant fits. */
    public boolean isFull() {
        return participants.size() >= GameStateSnapshot.MAXIMUM_PARTICIPANTS;
    }

    /** Returns the member authenticated as the account or guest principal, if any. */
    public Optional<LobbyParticipant> memberFor(UUID principalId) {
        return participants.stream().
                filter(member -> member.principalId().equals(principalId)).
                findFirst();
    }

    /** Returns whether a member already uses the display name, ignoring case. */
    public boolean hasDisplayName(String displayName) {
        return participants.stream().
                anyMatch(member -> member.displayName().equalsIgnoreCase(displayName));
    }

    /** Returns the member with the participant identity, if any. */
    public Optional<LobbyParticipant> member(GameParticipantId participantId) {
        return participants.stream().
                filter(member -> member.participantId().equals(participantId)).
                findFirst();
    }

    /** Returns whether the participant is the host. */
    public boolean isHost(GameParticipantId participantId) {
        return hostParticipantId.equals(participantId);
    }

    /** Returns whether at least two members are present and every non-host member is ready. */
    public boolean canStart() {
        return participants.size() >= MINIMUM_PARTICIPANTS
                && participants.stream().
                        filter(member -> !isHost(member.participantId())).
                        allMatch(LobbyParticipant::ready);
    }

    /** Appends a new, unready member in join order. */
    public LobbySnapshot join(LobbyParticipant participant, Instant now, Instant nextExpiresAt) {
        return join(participant, false, now, nextExpiresAt);
    }

    /**
     * Appends a new, unready member in join order and, when {@code host} is set, hands them the
     * host role; a previous host returning to a reopened lobby takes the role back.
     */
    public LobbySnapshot join(
            LobbyParticipant participant,
            boolean host,
            Instant now,
            Instant nextExpiresAt) {
        List<LobbyParticipant> next = new ArrayList<>(participants);
        next.add(participant.withReady(false));
        return next(host ? participant.participantId() : hostParticipantId, next, now, nextExpiresAt);
    }

    /**
     * Removes a member; when the host leaves, the earliest remaining registered member, else the
     * earliest member, becomes host. Returns empty once nobody is left.
     */
    public Optional<LobbySnapshot> leave(
            GameParticipantId participantId,
            Instant now,
            Instant nextExpiresAt) {
        List<LobbyParticipant> next = participants.stream().
                filter(member -> !member.participantId().equals(participantId)).
                toList();
        if (next.isEmpty()) {
            return Optional.empty();
        }
        GameParticipantId nextHost = isHost(participantId)
                ? next.stream().filter(LobbyParticipant::registered).findFirst().
                        orElse(next.getFirst()).participantId()
                : hostParticipantId;
        return Optional.of(next(nextHost, next, now, nextExpiresAt));
    }

    /** Sets one member's ready flag. */
    public LobbySnapshot withReady(
            GameParticipantId participantId,
            boolean ready,
            Instant now,
            Instant nextExpiresAt) {
        List<LobbyParticipant> next = participants.stream().
                map(member -> member.participantId().equals(participantId)
                        ? member.withReady(ready)
                        : member).
                toList();
        return next(hostParticipantId, next, now, nextExpiresAt);
    }

    /** Changes which devices play the round audio. */
    public LobbySnapshot withAudioMode(AudioMode nextAudioMode, Instant now, Instant nextExpiresAt) {
        return withSettings(configuration, nextAudioMode, now, nextExpiresAt);
    }

    /**
     * Changes the game rules, playlist, and audio mode. Members agreed to the previous settings,
     * so a changed game configuration clears every ready flag.
     */
    public LobbySnapshot withSettings(
            RequestedGameConfiguration nextConfiguration,
            AudioMode nextAudioMode,
            Instant now,
            Instant nextExpiresAt) {
        List<LobbyParticipant> nextParticipants = configuration.equals(nextConfiguration)
                ? participants
                : participants.stream().map(member -> member.withReady(false)).toList();
        return new LobbySnapshot(
                sessionId, code, hostParticipantId, nextParticipants, nextConfiguration,
                nextAudioMode, Math.addExact(version, 1), createdAt, now, nextExpiresAt,
                started, reopenedAs);
    }

    /** Marks the game as begun; the lobby is kept only for a later return to the lobby. */
    public LobbySnapshot start(Instant now, Instant nextExpiresAt) {
        return new LobbySnapshot(
                sessionId, code, hostParticipantId, participants, configuration, audioMode,
                Math.addExact(version, 1), createdAt, now, nextExpiresAt, true, Optional.empty());
    }

    /** Records the lobby that players of this started lobby's game return to. */
    public LobbySnapshot reopenAs(SessionId nextSessionId, Instant now) {
        return new LobbySnapshot(
                sessionId, code, hostParticipantId, participants, configuration, audioMode,
                Math.addExact(version, 1), createdAt, now, expiresAt, started,
                Optional.of(nextSessionId));
    }

    private LobbySnapshot next(
            GameParticipantId nextHost,
            List<LobbyParticipant> nextParticipants,
            Instant now,
            Instant nextExpiresAt) {
        return new LobbySnapshot(
                sessionId, code, nextHost, nextParticipants, configuration, audioMode,
                Math.addExact(version, 1), createdAt, now, nextExpiresAt, started, reopenedAs);
    }

    private static void requireUnique(List<LobbyParticipant> participants) {
        Set<GameParticipantId> ids = new HashSet<>();
        Set<UUID> accounts = new HashSet<>();
        for (LobbyParticipant member : participants) {
            Objects.requireNonNull(member, "participant is required");
            if (!ids.add(member.participantId())) {
                throw new IllegalArgumentException("participant identities must be unique");
            }
            member.accountId().ifPresent(account -> {
                if (!accounts.add(account)) {
                    throw new IllegalArgumentException("participant accounts must be unique");
                }
            });
        }
    }
}
