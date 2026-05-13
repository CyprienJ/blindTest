package com.blindtest.repository;

import com.blindtest.model.entity.RefreshTokenEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA repository for {@link RefreshTokenEntity}. */
public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, UUID> {

    /** Finds a refresh token by its opaque token value. */
    Optional<RefreshTokenEntity> findByToken(String token);

    /** Deletes all refresh tokens for the given user (full logout from all devices). */
    void deleteByUserId(UUID userId);
}
