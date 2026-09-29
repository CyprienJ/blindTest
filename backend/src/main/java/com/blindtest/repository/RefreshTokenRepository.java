package com.blindtest.repository;

import com.blindtest.model.entity.RefreshTokenEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data JPA repository for {@link RefreshTokenEntity}. */
public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, UUID> {

    /** Finds a refresh token by its opaque token value. */
    Optional<RefreshTokenEntity> findByToken(String token);

    /**
     * Deletes a refresh token by id in a single statement and returns the number of rows removed.
     * Returns 0 when a concurrent transaction already consumed the token.
     */
    @Modifying
    @Query("DELETE FROM RefreshTokenEntity t WHERE t.id = :id")
    int deleteByIdReturningCount(@Param("id") UUID id);

    /** Deletes all refresh tokens for the given user (full logout from all devices). */
    void deleteByUserId(UUID userId);
}
