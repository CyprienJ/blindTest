package com.blindtest.repository;

import com.blindtest.model.entity.ImportJobStatus;
import com.blindtest.model.entity.TrackImportJobEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data JPA repository for {@link TrackImportJobEntity}. */
public interface TrackImportJobRepository extends JpaRepository<TrackImportJobEntity, UUID> {

    /** Returns all import jobs with the given status. */
    List<TrackImportJobEntity> findByStatus(ImportJobStatus status);

    /** Returns IDs for persisted jobs whose processing lifecycle is still active. */
    @Query("SELECT j.id FROM TrackImportJobEntity j WHERE j.status IN :statuses")
    List<UUID> findIdsByStatusIn(@Param("statuses") Collection<ImportJobStatus> statuses);

    /** Returns all import jobs submitted by the given admin. */
    List<TrackImportJobEntity> findByAdminId(UUID adminId);

    /** Returns the job by ID with its track and admin eagerly loaded (avoids lazy-load issues). */
    @Query("SELECT j FROM TrackImportJobEntity j LEFT JOIN FETCH j.track LEFT JOIN FETCH j.admin WHERE j.id = :id")
    Optional<TrackImportJobEntity> findByIdWithTrack(@Param("id") UUID id);

    /** Locks the import workflow associated with a track while it is reviewed. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT j FROM TrackImportJobEntity j LEFT JOIN FETCH j.track "
            + "WHERE j.track.id = :trackId")
    Optional<TrackImportJobEntity> findByTrackIdForUpdate(@Param("trackId") UUID trackId);

    /** Reports whether a track belongs to an import in the requested lifecycle state. */
    boolean existsByTrackIdAndStatus(UUID trackId, ImportJobStatus status);

    /** Reports whether the video already has an import in an active processing state. */
    boolean existsByYoutubeVideoIdAndStatusIn(
            String youtubeVideoId, Collection<ImportJobStatus> statuses);
}
