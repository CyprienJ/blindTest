package com.blindtest.repository;

import com.blindtest.model.entity.UserEntity;
import com.blindtest.model.entity.UserRole;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

/** Spring Data JPA repository for {@link UserEntity}. */
public interface UserRepository extends JpaRepository<UserEntity, UUID> {

    /** Finds a user by email address. */
    Optional<UserEntity> findByEmail(String email);

    /** Finds a user by username. */
    Optional<UserEntity> findByUsername(String username);

    /** Returns {@code true} if a user with the given email already exists. */
    boolean existsByEmail(String email);

    /** Returns {@code true} if a user with the given username already exists. */
    boolean existsByUsername(String username);

    /** Returns users with the requested role. */
    List<UserEntity> findByRole(UserRole role);

    /**
     * Locks every existing user in stable order to serialize first-administrator provisioning.
     *
     * <p>Public registration can only add {@code USER} rows, so concurrent inserts do not
     * weaken the zero-administrator invariant.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT user FROM UserEntity user ORDER BY user.id")
    List<UserEntity> lockAllUsersForFirstAdminProvisioning();
}
