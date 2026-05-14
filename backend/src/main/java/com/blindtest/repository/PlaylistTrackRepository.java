package com.blindtest.repository;

import com.blindtest.model.entity.PlaylistTrackEntity;
import com.blindtest.model.entity.PlaylistTrackId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA repository for {@link PlaylistTrackEntity}. */
public interface PlaylistTrackRepository extends JpaRepository<PlaylistTrackEntity, PlaylistTrackId> {

    /** Returns all track entries for the given playlist, sorted by position ascending. */
    List<PlaylistTrackEntity> findByIdPlaylistIdOrderByTrackOrderAsc(UUID playlistId);
}
