package com.blindtest.repository;

import com.blindtest.model.entity.TrackAnswerAliasEntity;
import com.blindtest.model.entity.TrackAnswerType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repository for administrator-configured alternative track answers. */
public interface TrackAnswerAliasRepository
        extends JpaRepository<TrackAnswerAliasEntity, UUID> {

    /** Lists aliases in a stable administrator-facing order. */
    List<TrackAnswerAliasEntity> findByTrackIdOrderByAnswerTypeAscValueAscIdAsc(UUID trackId);

    /** Loads aliases for a bounded game-track snapshot in one query. */
    @Query("SELECT alias FROM TrackAnswerAliasEntity alias JOIN FETCH alias.track track "
            + "WHERE track.id IN :trackIds "
            + "ORDER BY track.id, alias.answerType, alias.value, alias.id")
    List<TrackAnswerAliasEntity> findAllByTrackIdIn(
            @Param("trackIds") List<UUID> trackIds);

    /** Finds an alias only when it belongs to the requested track. */
    Optional<TrackAnswerAliasEntity> findByIdAndTrackId(UUID id, UUID trackId);

    /** Detects a duplicate normalized alias of the same answer type. */
    boolean existsByTrackIdAndAnswerTypeAndNormalizedValue(
            UUID trackId, TrackAnswerType answerType, String normalizedValue);

    /** Detects a duplicate while excluding the alias being updated. */
    boolean existsByTrackIdAndAnswerTypeAndNormalizedValueAndIdNot(
            UUID trackId,
            TrackAnswerType answerType,
            String normalizedValue,
            UUID id);
}
