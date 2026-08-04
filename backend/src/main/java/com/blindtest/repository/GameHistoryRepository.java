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
