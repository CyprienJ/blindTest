package com.blindtest.repository;

import com.blindtest.model.entity.GameHistoryEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data repository for durable completed-game snapshots. */
public interface GameHistoryRepository extends JpaRepository<GameHistoryEntity, UUID> {

    /** Finds the unique durable snapshot created from one active session. */
    Optional<GameHistoryEntity> findBySessionId(UUID sessionId);

    /** Supports idempotent completion persistence keyed by active session identity. */
    boolean existsBySessionId(UUID sessionId);

    /** Finds a completed game only when the authenticated account took part in it. */
    @Query("""
            SELECT DISTINCT history
            FROM GameHistoryEntity history
            JOIN history.participants participant
            WHERE history.sessionId = :sessionId
              AND participant.user.id = :accountId
            """)
    Optional<GameHistoryEntity> findParticipatedBySessionIdAndAccountId(
            @Param("sessionId") UUID sessionId,
            @Param("accountId") UUID accountId);

    /** Finds a completed game only when the guest participant took part in it (ADR-0020). */
    @Query("""
            SELECT DISTINCT history
            FROM GameHistoryEntity history
            JOIN history.participants participant
            WHERE history.sessionId = :sessionId
              AND participant.user IS NULL
              AND participant.participantKind = com.blindtest.model.entity.HistoryParticipantKind.GUEST
              AND participant.participantId = :participantId
            """)
    Optional<GameHistoryEntity> findParticipatedBySessionIdAndGuestParticipantId(
            @Param("sessionId") UUID sessionId,
            @Param("participantId") UUID participantId);

    /** Finds a completed game only when the authenticated account is its snapshotted owner. */
    @Query("""
            SELECT DISTINCT history
            FROM GameHistoryEntity history
            JOIN history.participants participant
            WHERE history.sessionId = :sessionId
              AND participant.owner = true
              AND participant.user.id = :accountId
            """)
    Optional<GameHistoryEntity> findOwnedBySessionIdAndAccountId(
            @Param("sessionId") UUID sessionId,
            @Param("accountId") UUID accountId);
}
