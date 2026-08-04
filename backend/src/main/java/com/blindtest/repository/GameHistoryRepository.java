package com.blindtest.repository;

import com.blindtest.model.entity.GameHistoryEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for durable completed-game snapshots. */
public interface GameHistoryRepository extends JpaRepository<GameHistoryEntity, UUID> {

    /** Finds the unique durable snapshot created from one active session. */
    Optional<GameHistoryEntity> findBySessionId(UUID sessionId);

    /** Supports idempotent completion persistence keyed by active session identity. */
    boolean existsBySessionId(UUID sessionId);
}
