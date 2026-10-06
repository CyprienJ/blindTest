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
 * Immutable multiplayer lobby before its game exists (ADR-0019). Tracks are snapshotted only
 * when the host starts, so the lobby keeps the requested configuration. Its session ID becomes
 * the game session's ID at start.
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
        Instant expiresAt) {

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
                0, now, now, expiresAt);
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
        List<LobbyParticipant> next = new ArrayList<>(participants);
        next.add(participant.withReady(false));
        return next(hostParticipantId, next, now, nextExpiresAt);
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
        return new LobbySnapshot(
                sessionId, code, hostParticipantId, participants, configuration, nextAudioMode,
                Math.addExact(version, 1), createdAt, now, nextExpiresAt);
    }

    private LobbySnapshot next(
            GameParticipantId nextHost,
            List<LobbyParticipant> nextParticipants,
            Instant now,
            Instant nextExpiresAt) {
        return new LobbySnapshot(
                sessionId, code, nextHost, nextParticipants, configuration, audioMode,
                Math.addExact(version, 1), createdAt, now, nextExpiresAt);
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
