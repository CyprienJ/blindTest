package com.blindtest.repository;

import com.blindtest.model.entity.PlaylistTrackEntity;
import com.blindtest.model.entity.PlaylistTrackId;
import com.blindtest.model.entity.TrackStatus;
import java.util.List;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data JPA repository for {@link PlaylistTrackEntity}. */
public interface PlaylistTrackRepository extends JpaRepository<PlaylistTrackEntity, PlaylistTrackId> {

    /** Returns all track entries for the given playlist, sorted by position ascending. */
    List<PlaylistTrackEntity> findByIdPlaylistIdOrderByTrackOrderAsc(UUID playlistId);

    /** Returns all tracks in a playlist (all statuses) with tracks eagerly loaded, ordered by position. */
    @Query("SELECT pt FROM PlaylistTrackEntity pt JOIN FETCH pt.track "
            + "WHERE pt.id.playlistId = :playlistId ORDER BY pt.trackOrder ASC")
    List<PlaylistTrackEntity> findAllByPlaylistIdOrdered(@Param("playlistId") UUID playlistId);

    /** Locks every current membership while an ordered playlist snapshot is mutated. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT pt FROM PlaylistTrackEntity pt JOIN FETCH pt.track "
            + "WHERE pt.id.playlistId = :playlistId ORDER BY pt.trackOrder ASC")
    List<PlaylistTrackEntity> findAllByPlaylistIdForUpdate(
            @Param("playlistId") UUID playlistId);

    /** Returns published tracks in a playlist, eagerly loaded and ordered by position. */
    @Query("SELECT pt FROM PlaylistTrackEntity pt JOIN FETCH pt.track t "
            + "WHERE pt.id.playlistId = :playlistId AND t.status = :status "
            + "ORDER BY pt.trackOrder ASC")
    List<PlaylistTrackEntity> findPublishedByPlaylistIdOrdered(
            @Param("playlistId") UUID playlistId, @Param("status") TrackStatus status);

    /** Returns the number of published tracks in the given playlist. */
    @Query("SELECT COUNT(pt) FROM PlaylistTrackEntity pt JOIN pt.track t "
            + "WHERE pt.id.playlistId = :playlistId AND t.status = :status")
    int countPublishedInPlaylist(
            @Param("playlistId") UUID playlistId, @Param("status") TrackStatus status);

    /** Counts all tracks in the given playlist regardless of status. */
    int countByIdPlaylistId(UUID playlistId);

    /** Deletes all track entries for the given playlist. */
    @Modifying
    @Query("DELETE FROM PlaylistTrackEntity pt WHERE pt.id.playlistId = :playlistId")
    void deleteByIdPlaylistId(@Param("playlistId") UUID playlistId);

    /** Returns every playlist currently containing the given track. */
    @Query("SELECT pt.id.playlistId FROM PlaylistTrackEntity pt WHERE pt.id.trackId = :trackId")
    List<UUID> findPlaylistIdsByTrackId(@Param("trackId") UUID trackId);
}
