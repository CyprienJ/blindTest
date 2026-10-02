package com.blindtest.repository;

import com.blindtest.model.entity.InvitationEntity;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data JPA repository for {@link InvitationEntity}. */
public interface InvitationRepository extends JpaRepository<InvitationEntity, UUID> {

    /** Finds an invitation by the SHA-256 hash of its link token. */
    Optional<InvitationEntity> findByTokenHash(String tokenHash);

    /** Returns every invitation, newest first. */
    List<InvitationEntity> findAllByOrderByCreatedAtDesc();

    /** Revokes the open invitation for {@code email}, if any, so a replacement can be issued. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE InvitationEntity i SET i.revokedAt = :now "
            + "WHERE i.email = :email AND i.acceptedAt IS NULL AND i.revokedAt IS NULL")
    int revokeOpenForEmail(@Param("email") String email, @Param("now") LocalDateTime now);

    /**
     * Marks an open, unexpired invitation as accepted in a single statement and returns the
     * number of rows changed. Returns 0 when it was already used, revoked, or expired, so two
     * concurrent registrations cannot both consume the same invitation.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE InvitationEntity i SET i.acceptedAt = :now "
            + "WHERE i.id = :id AND i.acceptedAt IS NULL AND i.revokedAt IS NULL "
            + "AND i.expiresAt > :now")
    int acceptIfOpen(@Param("id") UUID id, @Param("now") LocalDateTime now);
}
