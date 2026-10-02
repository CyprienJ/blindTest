package com.blindtest.model.session;

import com.blindtest.model.game.GameConfiguration;
import com.blindtest.model.game.GameParticipantId;
import com.blindtest.model.game.GameStateSnapshot;
import com.blindtest.model.game.SecondaryDiscoveryState;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Immutable canonical state retained for one active server-authoritative session. */
public record ActiveSessionSnapshot(
        SessionId sessionId,
        GameParticipantId ownerParticipantId,
        List<SessionParticipant> participants,
        GameConfiguration configuration,
        GameStateSnapshot gameState,
        SecondaryDiscoveryState discoveries,
        ProcessedCommandLedger processedCommands,
        long version,
        Instant createdAt,
        Instant updatedAt,
        Instant expiresAt,
        long roundStartedVersion) {

    /** Defensively copies collections and enforces session/version/expiry invariants. */
    public ActiveSessionSnapshot {
        Objects.requireNonNull(sessionId, "sessionId is required");
        Objects.requireNonNull(ownerParticipantId, "ownerParticipantId is required");
        Objects.requireNonNull(participants, "participants are required");
        Objects.requireNonNull(configuration, "configuration is required");
        Objects.requireNonNull(gameState, "gameState is required");
        Objects.requireNonNull(discoveries, "discoveries are required");
        Objects.requireNonNull(processedCommands, "processedCommands are required");
        Objects.requireNonNull(createdAt, "createdAt is required");
        Objects.requireNonNull(updatedAt, "updatedAt is required");
        Objects.requireNonNull(expiresAt, "expiresAt is required");
        participants = List.copyOf(participants);
        if (participants.isEmpty()) {
            throw new IllegalArgumentException("participants are required");
        }
        requireUniqueParticipants(participants);
        if (participants.stream().noneMatch(
                participant -> participant.participantId().equals(ownerParticipantId))) {
            throw new IllegalArgumentException("owner must be an active participant");
        }
        if (!participants.stream().map(SessionParticipant::participantId).toList().
                equals(gameState.participants())) {
            throw new IllegalArgumentException("participants must match game state");
        }
        if (!configuration.equals(gameState.configuration())) {
            throw new IllegalArgumentException("configuration must match game state");
        }
        if (version < 0) {
            throw new IllegalArgumentException("version cannot be negative");
        }
        if (roundStartedVersion < 0 || roundStartedVersion > version) {
            throw new IllegalArgumentException("roundStartedVersion must not exceed version");
        }
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt cannot precede createdAt");
        }
        if (!expiresAt.isAfter(updatedAt)) {
            throw new IllegalArgumentException("expiresAt must follow updatedAt");
        }
    }

    /** Returns whether the session has expired at the inclusive authoritative instant. */
    public boolean isExpiredAt(Instant instant) {
        Objects.requireNonNull(instant, "instant is required");
        return !expiresAt.isAfter(instant);
    }

    /** Creates the next immutable version while retaining stable session metadata. */
    public ActiveSessionSnapshot next(
            GameStateSnapshot nextGameState,
            SecondaryDiscoveryState nextDiscoveries,
            Instant nextUpdatedAt,
            Instant nextExpiresAt) {
        return next(
                nextGameState,
                nextDiscoveries,
                processedCommands,
                nextUpdatedAt,
                nextExpiresAt);
    }

    /** Creates the next version with updated game, discoveries, and idempotency state. */
    public ActiveSessionSnapshot next(
            GameStateSnapshot nextGameState,
            SecondaryDiscoveryState nextDiscoveries,
            ProcessedCommandLedger nextProcessedCommands,
            Instant nextUpdatedAt,
            Instant nextExpiresAt) {
        return new ActiveSessionSnapshot(
                sessionId,
                ownerParticipantId,
                participants,
                configuration,
                nextGameState,
                nextDiscoveries,
                nextProcessedCommands,
                Math.addExact(version, 1),
                createdAt,
                nextUpdatedAt,
                nextExpiresAt,
                roundStartedVersion);
    }

    /** Returns whether more than one participant plays this session. */
    public boolean isMultiplayer() {
        return participants.size() > 1;
    }

    /**
     * Marks this version as the start of the current round; multiplayer round commands sent
     * from an earlier version belong to a previous round.
     */
    public ActiveSessionSnapshot markRoundStarted() {
        return new ActiveSessionSnapshot(
                sessionId, ownerParticipantId, participants, configuration, gameState,
                discoveries, processedCommands, version, createdAt, updatedAt, expiresAt, version);
    }

    private static void requireUniqueParticipants(List<SessionParticipant> participants) {
        Set<GameParticipantId> participantIds = new HashSet<>();
        Set<java.util.UUID> accountIds = new HashSet<>();
        for (SessionParticipant participant : participants) {
            Objects.requireNonNull(participant, "participant is required");
            if (!participantIds.add(participant.participantId())) {
                throw new IllegalArgumentException("participant identities must be unique");
            }
            participant.accountId().ifPresent(accountId -> {
                if (!accountIds.add(accountId)) {
                    throw new IllegalArgumentException("participant accounts must be unique");
                }
            });
        }
    }
}
