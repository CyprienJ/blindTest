package com.blindtest.repository;

import com.blindtest.model.entity.ImportJobStatus;
import com.blindtest.model.entity.TrackImportJobEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data JPA repository for {@link TrackImportJobEntity}. */
public interface TrackImportJobRepository extends JpaRepository<TrackImportJobEntity, UUID> {

    /** Returns all import jobs with the given status. */
    List<TrackImportJobEntity> findByStatus(ImportJobStatus status);

    /** Returns all import jobs submitted by the given admin. */
    List<TrackImportJobEntity> findByAdminId(UUID adminId);

    /** Returns the job by ID with its track and admin eagerly loaded (avoids lazy-load issues). */
    @Query("SELECT j FROM TrackImportJobEntity j LEFT JOIN FETCH j.track LEFT JOIN FETCH j.admin WHERE j.id = :id")
    Optional<TrackImportJobEntity> findByIdWithTrack(@Param("id") UUID id);

    /** Reports whether a track belongs to an import in the requested lifecycle state. */
    boolean existsByTrackIdAndStatus(UUID trackId, ImportJobStatus status);
}
