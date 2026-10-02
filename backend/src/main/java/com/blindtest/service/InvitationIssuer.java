package com.blindtest.service;

import com.blindtest.config.properties.ApplicationProperties;
import com.blindtest.model.entity.InvitationEntity;
import com.blindtest.model.entity.UserEntity;
import com.blindtest.repository.InvitationRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates invitation rows and their one-time link tokens. Shared by the administrator API and
 * the first-administrator bootstrap command; only the SHA-256 hash of a token is persisted.
 */
@Component
public class InvitationIssuer {

    /** How long an invitation link stays usable. */
    public static final Duration INVITATION_TTL = Duration.ofDays(2);

    private static final int TOKEN_BYTES = 32;

    private final InvitationRepository invitationRepository;
    private final Clock clock;
    private final String appBaseUrl;
    private final SecureRandom secureRandom = new SecureRandom();

    /** Constructs the issuer with its repository, clock, and public base URL. */
    public InvitationIssuer(
            InvitationRepository invitationRepository,
            Clock clock,
            ApplicationProperties applicationProperties) {
        this.invitationRepository = invitationRepository;
        this.clock = clock;
        this.appBaseUrl = applicationProperties.baseUrl().toString().replaceAll("/+$", "");
    }

    /**
     * Revokes any open invitation for {@code email} and issues a new one.
     *
     * @param invitedBy the inviting administrator, or {@code null} for the bootstrap invitation
     */
    @Transactional
    public IssuedInvitation issue(String email, UserEntity invitedBy) {
        String normalizedEmail = normalizeEmail(email);
        LocalDateTime now = LocalDateTime.now(clock);
        invitationRepository.revokeOpenForEmail(normalizedEmail, now);

        String token = newToken();
        InvitationEntity invitation = new InvitationEntity();
        invitation.setEmail(normalizedEmail);
        invitation.setTokenHash(hashToken(token));
        invitation.setInvitedBy(invitedBy);
        invitation.setCreatedAt(now);
        invitation.setExpiresAt(now.plus(INVITATION_TTL));
        InvitationEntity saved = invitationRepository.save(invitation);
        return new IssuedInvitation(saved, token, registrationLink(token));
    }

    /** Returns the public registration link that carries {@code token}. */
    public String registrationLink(String token) {
        return appBaseUrl + "/auth/register?invite=" + token;
    }

    /** Lowercases and trims an email address the same way user accounts are matched. */
    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    /** Returns the hex-encoded SHA-256 hash under which a token is stored. */
    public static String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** A freshly issued invitation together with its raw token, which is never persisted. */
    public record IssuedInvitation(InvitationEntity invitation, String token, String link) {
    }
}
