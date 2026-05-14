package com.blindtest.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.blindtest.model.entity.UserEntity;
import com.blindtest.model.entity.UserRole;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    private UserEntity savedUser;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        UserEntity user = new UserEntity();
        user.setUsername("alice");
        user.setEmail("alice@example.com");
        user.setPasswordHash("$2a$10$hash");
        user.setRole(UserRole.USER);
        savedUser = userRepository.save(user);
    }

    @Test
    void findByEmailReturnsUserWhenEmailExists() {
        Optional<UserEntity> result = userRepository.findByEmail("alice@example.com");
        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(savedUser.getId());
    }

    @Test
    void findByEmailReturnsEmptyWhenEmailNotFound() {
        Optional<UserEntity> result = userRepository.findByEmail("unknown@example.com");
        assertThat(result).isEmpty();
    }

    @Test
    void findByUsernameReturnsUserWhenUsernameExists() {
        Optional<UserEntity> result = userRepository.findByUsername("alice");
        assertThat(result).isPresent();
        assertThat(result.get().getEmail()).isEqualTo("alice@example.com");
    }

    @Test
    void findByUsernameReturnsEmptyWhenUsernameNotFound() {
        Optional<UserEntity> result = userRepository.findByUsername("nobody");
        assertThat(result).isEmpty();
    }

    @Test
    void existsByEmailReturnsTrueWhenEmailExists() {
        assertThat(userRepository.existsByEmail("alice@example.com")).isTrue();
    }

    @Test
    void existsByEmailReturnsFalseWhenEmailNotFound() {
        assertThat(userRepository.existsByEmail("unknown@example.com")).isFalse();
    }

    @Test
    void existsByUsernameReturnsTrueWhenUsernameExists() {
        assertThat(userRepository.existsByUsername("alice")).isTrue();
    }

    @Test
    void existsByUsernameReturnsFalseWhenUsernameNotFound() {
        assertThat(userRepository.existsByUsername("nobody")).isFalse();
    }
}
