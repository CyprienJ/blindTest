package com.blindtest.repository;

import com.blindtest.model.entity.ImportJobStatus;
import com.blindtest.model.entity.TrackImportJobEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA repository for {@link TrackImportJobEntity}. */
public interface TrackImportJobRepository extends JpaRepository<TrackImportJobEntity, UUID> {

    /** Returns all import jobs with the given status. */
    List<TrackImportJobEntity> findByStatus(ImportJobStatus status);

    /** Returns all import jobs submitted by the given admin. */
    List<TrackImportJobEntity> findByAdminId(UUID adminId);
}
