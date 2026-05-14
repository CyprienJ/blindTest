package com.blindtest.repository;

import com.blindtest.model.entity.PlaylistEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA repository for {@link PlaylistEntity}. */
public interface PlaylistRepository extends JpaRepository<PlaylistEntity, UUID> {

    /** Returns all playlists created by the given user. */
    List<PlaylistEntity> findByCreatedById(UUID userId);
}
