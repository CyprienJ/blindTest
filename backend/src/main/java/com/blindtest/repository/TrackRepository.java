package com.blindtest.repository;

import com.blindtest.model.entity.TrackEntity;
import com.blindtest.model.entity.TrackStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA repository for {@link TrackEntity}. */
public interface TrackRepository extends JpaRepository<TrackEntity, UUID> {

    /** Returns all tracks with the given publication status. */
    List<TrackEntity> findByStatus(TrackStatus status);

    /** Returns all tracks belonging to the given category. */
    List<TrackEntity> findByCategory(String category);

    /** Returns all tracks from the given decade (e.g. {@code 1980} for the 80s). */
    List<TrackEntity> findByDecade(Short decade);
}
