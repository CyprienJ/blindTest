package com.blindtest.repository;

import com.blindtest.model.entity.UserEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
