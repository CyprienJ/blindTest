package com.blindtest.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import com.blindtest.model.entity.UserRole;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtTokenProviderTest {

    private static final String SECRET = "test-secret-key-that-is-32-bytes!!";
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private JwtTokenProvider provider;

    @BeforeEach
    void setUp() {
        provider = new JwtTokenProvider(SECRET, 900_000L, 86_400_000L);
    }

    @Test
    void generateAccessTokenReturnsNonBlankToken() {
        String token = provider.generateAccessToken(USER_ID, UserRole.USER);
        assertThat(token).isNotBlank();
    }

    @Test
    void generateAccessTokenExtractsCorrectUserId() {
        String token = provider.generateAccessToken(USER_ID, UserRole.USER);
        assertThat(provider.extractUserId(token)).isEqualTo(USER_ID);
    }

    @Test
    void generateAccessTokenExtractsCorrectRoleUser() {
        String token = provider.generateAccessToken(USER_ID, UserRole.USER);
        assertThat(provider.extractRole(token)).isEqualTo(UserRole.USER);
    }

    @Test
    void generateAccessTokenExtractsCorrectRoleAdmin() {
        String token = provider.generateAccessToken(USER_ID, UserRole.ADMIN);
        assertThat(provider.extractRole(token)).isEqualTo(UserRole.ADMIN);
    }

    @Test
    void validateTokenReturnsTrueForValidToken() {
        String token = provider.generateAccessToken(USER_ID, UserRole.USER);
        assertThat(provider.validateToken(token)).isTrue();
    }

    @Test
    void validateTokenReturnsFalseForExpiredToken() {
        JwtTokenProvider shortLived = new JwtTokenProvider(SECRET, -1L, 86_400_000L);
        String expiredToken = shortLived.generateAccessToken(USER_ID, UserRole.USER);
        assertThat(provider.validateToken(expiredToken)).isFalse();
    }

    @Test
    void validateTokenReturnsFalseForTamperedSignature() {
        String token = provider.generateAccessToken(USER_ID, UserRole.USER) + "x";
        assertThat(provider.validateToken(token)).isFalse();
    }

    @Test
    void validateTokenReturnsFalseForTokenSignedWithDifferentKey() {
        JwtTokenProvider other = new JwtTokenProvider("other-secret-key-that-is-32-bytes!!", 900_000L, 86_400_000L);
        String token = other.generateAccessToken(USER_ID, UserRole.USER);
        assertThat(provider.validateToken(token)).isFalse();
    }

    @Test
    void validateTokenReturnsFalseForBlankString() {
        assertThat(provider.validateToken("")).isFalse();
    }

    @Test
    void generateRefreshTokenReturnsValidUuidString() {
        String refreshToken = provider.generateRefreshToken();
        assertThatNoException().isThrownBy(() -> UUID.fromString(refreshToken));
    }

    @Test
    void generateRefreshTokenReturnsUniqueValues() {
        String first = provider.generateRefreshToken();
        String second = provider.generateRefreshToken();
        assertThat(first).isNotEqualTo(second);
    }
}
