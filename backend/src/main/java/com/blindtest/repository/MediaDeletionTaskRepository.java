package com.blindtest.repository;

import com.blindtest.model.entity.MediaDeletionTaskEntity;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence boundary for durable object-deletion requests. */
public interface MediaDeletionTaskRepository extends JpaRepository<MediaDeletionTaskEntity, UUID> {

    /** Returns the next bounded batch of deletion tasks ready to run. */
    List<MediaDeletionTaskEntity> findTop25ByNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
            LocalDateTime now);

    /** Finds an existing idempotent deletion request for an object. */
    Optional<MediaDeletionTaskEntity> findByObjectKey(String objectKey);
}
