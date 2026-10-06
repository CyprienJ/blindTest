package com.blindtest.security;

import com.blindtest.model.entity.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
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
    private static final String PURPOSE_PASSWORD_RESET = "PASSWORD_RESET";
    private static final String CLAIM_SESSION = "sid";
    private static final String GUEST_AUDIENCE = "guest";

    private final SecretKey signingKey;
    private final long accessTokenTtlMs;
    private final long emailVerificationTtlMs;
    private final long passwordResetTtlMs;
    private final long guestTokenTtlMs;
    private final Clock clock;
    private final TokenGenerator tokenGenerator;

    /** Initializes the provider with the signing key and token TTLs. */
    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token.ttl-ms:900000}") long accessTokenTtlMs,
            @Value("${jwt.email-verification.ttl-ms:86400000}") long emailVerificationTtlMs,
            @Value("${jwt.password-reset.ttl-ms:3600000}") long passwordResetTtlMs,
            @Value("${jwt.guest-token.ttl-ms:21600000}") long guestTokenTtlMs,
            Clock clock,
            TokenGenerator tokenGenerator) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenTtlMs = accessTokenTtlMs;
        this.emailVerificationTtlMs = emailVerificationTtlMs;
        this.passwordResetTtlMs = passwordResetTtlMs;
        this.guestTokenTtlMs = guestTokenTtlMs;
        this.clock = clock;
        this.tokenGenerator = tokenGenerator;
    }

    /**
     * Generates a signed HS256 JWT containing {@code userId} (subject) and {@code role} claims.
     * The token expires after the configured TTL (default: 15 minutes).
     */
    public String generateAccessToken(UUID userId, UserRole role) {
        Date now = Date.from(clock.instant());
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
        return tokenGenerator.generate();
    }

    /**
     * Returns {@code true} if the token is an unexpired account access token with a valid HS256
     * signature; {@code false} for any expired, malformed, incorrectly signed, or guest token.
     */
    public boolean validateToken(String token) {
        try {
            Claims claims = parseClaims(token);
            return !isGuest(claims) && claims.get(CLAIM_ROLE, String.class) != null;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Generates a guest participant token (ADR-0020) with a distinct {@code guest} audience. It
     * carries no account or role, so account endpoints and {@link #validateToken} reject it; the
     * server also stops honouring it once the guest leaves its session or the session ends.
     */
    public String generateGuestToken(UUID sessionId, UUID participantId) {
        Date now = Date.from(clock.instant());
        Date expiry = new Date(now.getTime() + guestTokenTtlMs);
        var builder = Jwts.builder().subject(participantId.toString());
        builder.audience().add(GUEST_AUDIENCE).and().claim(CLAIM_SESSION, sessionId.toString());
        builder.issuedAt(now).expiration(expiry).signWith(signingKey);
        return builder.compact();
    }

    /** Returns the claims of a valid, unexpired guest token, or empty for any other token. */
    public Optional<GuestToken> parseGuestToken(String token) {
        try {
            Claims claims = parseClaims(token);
            if (!isGuest(claims)) {
                return Optional.empty();
            }
            return Optional.of(new GuestToken(
                    UUID.fromString(claims.get(CLAIM_SESSION, String.class)),
                    UUID.fromString(claims.getSubject())));
        } catch (JwtException | IllegalArgumentException | NullPointerException e) {
            return Optional.empty();
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
        Date now = Date.from(clock.instant());
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

    /**
     * Generates a signed HS256 JWT with {@code purpose=PASSWORD_RESET} for the given user.
     * The token expires after the configured password-reset TTL (default: 1 hour).
     */
    public String generatePasswordResetToken(UUID userId) {
        Date now = Date.from(clock.instant());
        Date expiry = new Date(now.getTime() + passwordResetTtlMs);
        var builder = Jwts.builder().subject(userId.toString());
        builder.claim(CLAIM_PURPOSE, PURPOSE_PASSWORD_RESET);
        builder.issuedAt(now).expiration(expiry).signWith(signingKey);
        return builder.compact();
    }

    /**
     * Validates a password-reset token: checks signature, expiry, and purpose claim.
     * Returns {@code false} for any expired, malformed, wrongly-signed, or wrong-purpose token.
     */
    public boolean validatePasswordResetToken(String token) {
        try {
            Claims claims = parseClaims(token);
            return PURPOSE_PASSWORD_RESET.equals(claims.get(CLAIM_PURPOSE, String.class));
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /** Extracts the user ID from a valid password-reset token. */
    public UUID extractUserIdFromPasswordResetToken(String token) {
        return UUID.fromString(parseClaims(token).getSubject());
    }

    private static boolean isGuest(Claims claims) {
        return claims.getAudience() != null && claims.getAudience().contains(GUEST_AUDIENCE);
    }

    private Claims parseClaims(String token) {
        var jwtClock = (io.jsonwebtoken.Clock) () -> Date.from(Instant.now(clock));
        var jws = Jwts.parser().verifyWith(signingKey).clock(jwtClock).build().parseSignedClaims(token);
        return jws.getPayload();
    }
}
