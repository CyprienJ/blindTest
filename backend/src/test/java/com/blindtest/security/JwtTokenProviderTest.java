package com.blindtest.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.blindtest.model.entity.UserRole;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtTokenProviderTest {

    private static final String SECRET = "test-secret-key-that-is-32-bytes!!";
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-07-28T08:00:00Z"), ZoneOffset.UTC);

    private JwtTokenProvider provider;

    @BeforeEach
    void setUp() {
        AtomicInteger tokenSequence = new AtomicInteger();
        TokenGenerator tokenGenerator = () -> "refresh-token-" + tokenSequence.incrementAndGet();
        provider = new JwtTokenProvider(
                SECRET, 900_000L, 86_400_000L, 3_600_000L, CLOCK, tokenGenerator);
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
        JwtTokenProvider shortLived = new JwtTokenProvider(
                SECRET, -1L, 86_400_000L, 3_600_000L, CLOCK, () -> "unused");
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
        JwtTokenProvider other = new JwtTokenProvider(
                "other-secret-key-that-is-32-bytes!!", 900_000L, 86_400_000L, 3_600_000L,
                CLOCK, () -> "unused");
        String token = other.generateAccessToken(USER_ID, UserRole.USER);
        assertThat(provider.validateToken(token)).isFalse();
    }

    @Test
    void validateTokenReturnsFalseForBlankString() {
        assertThat(provider.validateToken("")).isFalse();
    }

    @Test
    void generateRefreshTokenUsesInjectedGenerator() {
        String refreshToken = provider.generateRefreshToken();
        assertThat(refreshToken).isEqualTo("refresh-token-1");
    }

    @Test
    void generateRefreshTokenReturnsUniqueValues() {
        String first = provider.generateRefreshToken();
        String second = provider.generateRefreshToken();
        assertThat(first).isNotEqualTo(second);
    }
}
