package com.blindtest.service;

import com.blindtest.model.dto.RegisterRequest;
import com.blindtest.model.dto.RegisterResponse;
import com.blindtest.model.entity.InvitationEntity;
import com.blindtest.model.entity.UserEntity;
import com.blindtest.repository.UserRepository;
import com.blindtest.service.port.EmailSender;
import com.blindtest.service.port.TurnstileVerifier;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Creates accounts from invitations; registration is invitation-only. */
@Service
@RequiredArgsConstructor
public class RegistrationService {

    private final UserRepository userRepository;
    private final InvitationService invitationService;
    private final PasswordEncoder passwordEncoder;
    private final TurnstileVerifier turnstileVerifier;
    private final EmailSender emailSender;

    /**
     * Creates an account from an invitation.
     *
     * <p>Validates the Turnstile token, consumes the invitation, and creates a {@code USER}
     * account for the invited address. Opening the emailed link proves mailbox ownership, so the
     * account is created already verified and no verification email is sent. Any failure rolls
     * back the transaction, leaving the invitation usable.
     *
     * @throws ResponseStatusException 403 if Turnstile validation fails
     * @throws ResponseStatusException 400 if the invitation is invalid, used, revoked, or expired
     * @throws ResponseStatusException 409 if the email or username is already taken
     */
    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        if (!turnstileVerifier.verify(request.getTurnstileToken())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "TURNSTILE_FAILED");
        }
        InvitationEntity invitation = invitationService.consume(request.getInviteToken());
        String email = InvitationIssuer.normalizeEmail(invitation.getEmail());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "EMAIL_TAKEN");
        }
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "USERNAME_TAKEN");
        }
        UserEntity user = new UserEntity();
        user.setUsername(request.getUsername());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setEmailVerified(true);
        UserEntity saved = userRepository.save(user);
        invitationService.recordAcceptedUser(invitation.getId(), saved.getId());

        emailSender.sendWelcomeEmail(saved.getEmail(), saved.getUsername());

        return new RegisterResponse(saved.getId(), saved.getUsername(), saved.getEmail());
    }
}
