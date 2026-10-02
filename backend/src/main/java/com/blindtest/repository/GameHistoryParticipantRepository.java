package com.blindtest.repository;

import com.blindtest.model.entity.GameHistoryParticipantEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Spring Data repository for participant identity and final-score snapshots. */
public interface GameHistoryParticipantRepository
        extends JpaRepository<GameHistoryParticipantEntity, UUID> {

    /** Returns the retained participants for one completed game. */
    List<GameHistoryParticipantEntity> findByGameHistoryId(UUID gameHistoryId);

    /** Returns {@code [userId, completedGameCount]} pairs for every account with history. */
    @Query("""
            SELECT participant.user.id, COUNT(participant)
            FROM GameHistoryParticipantEntity participant
            WHERE participant.user IS NOT NULL
            GROUP BY participant.user.id
            """)
    List<Object[]> countGamesByUser();
}
