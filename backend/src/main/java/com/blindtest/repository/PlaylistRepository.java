package com.blindtest.repository;

import com.blindtest.model.entity.PlaylistEntity;
import java.util.List;
import java.util.UUID;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA repository for {@link PlaylistEntity}. */
public interface PlaylistRepository extends JpaRepository<PlaylistEntity, UUID> {

    /** Returns all playlists created by the given user. */
    List<PlaylistEntity> findByCreatedById(UUID userId);

    /** Counts playlists created by the given user. */
    long countByCreatedById(UUID userId);

    /** Returns public playlists in a stable name and identifier order. */
    List<PlaylistEntity> findAllByOrderByNameAscIdAsc();

    /** Locks a playlist so membership updates serialize even when it is currently empty. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT playlist FROM PlaylistEntity playlist WHERE playlist.id = :id")
    Optional<PlaylistEntity> findByIdForUpdate(@Param("id") UUID id);
}
