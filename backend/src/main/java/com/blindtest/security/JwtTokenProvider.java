package com.blindtest.security;

import com.blindtest.model.entity.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Generates and validates HS256 JWT access tokens; issues opaque UUID refresh token strings. */
@Component
public class JwtTokenProvider {

    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_PURPOSE = "purpose";
    private static final String PURPOSE_EMAIL_VERIFY = "EMAIL_VERIFY";

    private final SecretKey signingKey;
    private final long accessTokenTtlMs;
    private final long emailVerificationTtlMs;

    /** Initializes the provider with the signing key, access-token TTL, and email-verification TTL. */
    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token.ttl-ms:900000}") long accessTokenTtlMs,
            @Value("${jwt.email-verification.ttl-ms:86400000}") long emailVerificationTtlMs) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenTtlMs = accessTokenTtlMs;
        this.emailVerificationTtlMs = emailVerificationTtlMs;
    }

    /**
     * Generates a signed HS256 JWT containing {@code userId} (subject) and {@code role} claims.
     * The token expires after the configured TTL (default: 15 minutes).
     */
    public String generateAccessToken(UUID userId, UserRole role) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + accessTokenTtlMs);
        var jwt = Jwts.builder().subject(userId.toString()).claim(CLAIM_ROLE, role.name());
        jwt.issuedAt(now).expiration(expiry).signWith(signingKey);
        return jwt.compact();
    }

    /**
     * Returns a random UUID string intended as an opaque refresh token.
     * Callers are responsible for persisting it and enforcing the 7-day TTL.
     */
    public String generateRefreshToken() {
        return UUID.randomUUID().toString();
    }

    /**
     * Returns {@code true} if the token has a valid HS256 signature and has not expired;
     * {@code false} for any expired, malformed, or incorrectly signed token.
     */
    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /** Extracts the user ID from the subject claim of a valid access token. */
    public UUID extractUserId(String token) {
        return UUID.fromString(parseClaims(token).getSubject());
    }

    /** Extracts the role from a valid access token. */
    public UserRole extractRole(String token) {
        return UserRole.valueOf(parseClaims(token).get(CLAIM_ROLE, String.class));
    }

    /**
     * Generates a signed HS256 JWT with {@code purpose=EMAIL_VERIFY} for the given user.
     * The token expires after the configured email-verification TTL (default: 24 hours).
     */
    public String generateEmailVerificationToken(UUID userId) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + emailVerificationTtlMs);
        var builder = Jwts.builder().subject(userId.toString());
        builder.claim(CLAIM_PURPOSE, PURPOSE_EMAIL_VERIFY);
        builder.issuedAt(now).expiration(expiry).signWith(signingKey);
        return builder.compact();
    }

    /**
     * Validates an email-verification token: checks signature, expiry, and purpose claim.
     * Returns {@code false} for any expired, malformed, wrongly-signed, or wrong-purpose token.
     */
    public boolean validateEmailVerificationToken(String token) {
        try {
            Claims claims = parseClaims(token);
            return PURPOSE_EMAIL_VERIFY.equals(claims.get(CLAIM_PURPOSE, String.class));
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /** Extracts the user ID from a valid email-verification token. */
    public UUID extractUserIdFromEmailVerificationToken(String token) {
        return UUID.fromString(parseClaims(token).getSubject());
    }

    private Claims parseClaims(String token) {
        var jws = Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token);
        return jws.getPayload();
    }
}
