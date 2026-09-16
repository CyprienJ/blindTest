package com.blindtest.repository;

import com.blindtest.model.entity.GameHistoryRoundEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for ordered immutable round snapshots. */
public interface GameHistoryRoundRepository extends JpaRepository<GameHistoryRoundEntity, UUID> {

    /** Returns one completed game's round snapshots in authoritative play order. */
    List<GameHistoryRoundEntity> findByGameHistoryIdOrderByRoundIndex(UUID gameHistoryId);
}
