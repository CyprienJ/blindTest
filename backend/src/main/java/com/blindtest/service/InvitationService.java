package com.blindtest.service;

import com.blindtest.model.dto.InvitationLookupResponse;
import com.blindtest.model.dto.InvitationResponse;
import com.blindtest.model.entity.InvitationEntity;
import com.blindtest.model.entity.InvitationStatus;
import com.blindtest.model.entity.UserEntity;
import com.blindtest.repository.InvitationRepository;
import com.blindtest.repository.UserRepository;
import com.blindtest.service.InvitationIssuer.IssuedInvitation;
import com.blindtest.service.port.EmailSender;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Administrator invitation management and the invitation checks used by registration. */
@Service
@RequiredArgsConstructor
public class InvitationService {

    private final InvitationRepository invitationRepository;
    private final UserRepository userRepository;
    private final InvitationIssuer invitationIssuer;
    private final EmailSender emailSender;
    private final Clock clock;

    /**
     * Invites {@code email}, replacing any open invitation for the same address, and emails the
     * registration link.
     *
     * @throws ResponseStatusException 409 if an account already uses the address
     */
    @Transactional
    public InvitationResponse invite(String email, UUID adminId) {
        String normalizedEmail = InvitationIssuer.normalizeEmail(email);
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "EMAIL_TAKEN");
        }
        UserEntity admin = userRepository.findById(adminId).
                orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        IssuedInvitation issued = invitationIssuer.issue(normalizedEmail, admin);
        emailSender.sendInvitationEmail(
                normalizedEmail, admin.getUsername(), issued.token(), InvitationIssuer.INVITATION_TTL);
        return toResponse(issued.invitation());
    }

    /** Returns every invitation, newest first. */
    @Transactional(readOnly = true)
    public List<InvitationResponse> list() {
        return invitationRepository.findAllByOrderByCreatedAtDesc().stream().
                map(this::toResponse).
                toList();
    }

    /**
     * Issues a fresh link for the invitation's address, which invalidates the previous link.
     *
     * @throws ResponseStatusException 404 if unknown, 409 if already accepted
     */
    @Transactional
    public InvitationResponse resend(UUID invitationId, UUID adminId) {
        InvitationEntity invitation = findOrThrow(invitationId);
        if (invitation.getAcceptedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "INVITATION_ALREADY_ACCEPTED");
        }
        return invite(invitation.getEmail(), adminId);
    }

    /**
     * Revokes an invitation so its link stops working. Revoking twice is a no-op.
     *
     * @throws ResponseStatusException 404 if unknown, 409 if already accepted
     */
    @Transactional
    public void revoke(UUID invitationId) {
        InvitationEntity invitation = findOrThrow(invitationId);
        if (invitation.getAcceptedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "INVITATION_ALREADY_ACCEPTED");
        }
        if (invitation.getRevokedAt() == null) {
            invitation.setRevokedAt(LocalDateTime.now(clock));
            invitationRepository.save(invitation);
        }
    }

    /**
     * Returns the address of a usable invitation for the registration form.
     *
     * @throws ResponseStatusException 400 if the token is unknown, used, revoked, or expired
     */
    @Transactional(readOnly = true)
    public InvitationLookupResponse lookup(String token) {
        InvitationEntity invitation = findOpen(token).
                orElseThrow(InvitationService::invalidInvitation);
        return new InvitationLookupResponse(invitation.getEmail(), utc(invitation.getExpiresAt()));
    }

    /**
     * Consumes the invitation behind {@code token} within the caller's transaction and returns
     * the invited address. A concurrent caller using the same token gets 400.
     *
     * @throws ResponseStatusException 400 if the token is unknown, used, revoked, or expired
     */
    @Transactional
    public InvitationEntity consume(String token) {
        InvitationEntity invitation = findOpen(token).
                orElseThrow(InvitationService::invalidInvitation);
        if (invitationRepository.acceptIfOpen(invitation.getId(), LocalDateTime.now(clock)) != 1) {
            throw invalidInvitation();
        }
        return invitationRepository.findById(invitation.getId()).orElseThrow();
    }

    /** Records which account accepted a consumed invitation. */
    @Transactional
    public void recordAcceptedUser(UUID invitationId, UUID userId) {
        InvitationEntity invitation = findOrThrow(invitationId);
        invitation.setAcceptedUserId(userId);
        invitationRepository.save(invitation);
    }

    private Optional<InvitationEntity> findOpen(String token) {
        LocalDateTime now = LocalDateTime.now(clock);
        return invitationRepository.findByTokenHash(InvitationIssuer.hashToken(token)).
                filter(invitation -> invitation.statusAt(now) == InvitationStatus.PENDING);
    }

    private InvitationEntity findOrThrow(UUID invitationId) {
        return invitationRepository.findById(invitationId).
                orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private static ResponseStatusException invalidInvitation() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVITATION_INVALID");
    }

    private InvitationResponse toResponse(InvitationEntity invitation) {
        UserEntity inviter = invitation.getInvitedBy();
        return new InvitationResponse(
                invitation.getId(),
                invitation.getEmail(),
                invitation.statusAt(LocalDateTime.now(clock)),
                inviter == null ? null : inviter.getUsername(),
                utc(invitation.getCreatedAt()),
                utc(invitation.getExpiresAt()),
                utc(invitation.getAcceptedAt()),
                utc(invitation.getRevokedAt()));
    }

    private static Instant utc(LocalDateTime timestamp) {
        return timestamp == null ? null : timestamp.toInstant(ZoneOffset.UTC);
    }
}
