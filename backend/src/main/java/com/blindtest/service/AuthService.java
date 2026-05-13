package com.blindtest.service;

import com.blindtest.model.dto.RegisterRequest;
import com.blindtest.model.dto.RegisterResponse;
import com.blindtest.model.entity.UserEntity;
import com.blindtest.repository.UserRepository;
import com.blindtest.security.JwtTokenProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Business logic for user registration and email verification. */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TurnstileService turnstileService;
    private final JwtTokenProvider jwtTokenProvider;
    private final EmailService emailService;

    /** Constructs the service with all required dependencies. */
    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            TurnstileService turnstileService,
            JwtTokenProvider jwtTokenProvider,
            EmailService emailService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.turnstileService = turnstileService;
        this.jwtTokenProvider = jwtTokenProvider;
        this.emailService = emailService;
    }

    /**
     * Registers a new user account.
     *
     * <p>Validates the Turnstile token, checks for duplicate email/username,
     * hashes the password, persists the user, and sends a verification email.
     *
     * @throws ResponseStatusException 403 if Turnstile validation fails
     * @throws ResponseStatusException 409 if email or username is already taken
     */
    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        if (!turnstileService.verify(request.getTurnstileToken())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "TURNSTILE_FAILED");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "EMAIL_TAKEN");
        }
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "USERNAME_TAKEN");
        }
        UserEntity user = new UserEntity();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        UserEntity saved = userRepository.save(user);

        String verificationToken = jwtTokenProvider.generateEmailVerificationToken(saved.getId());
        emailService.sendVerificationEmail(saved.getEmail(), saved.getUsername(), verificationToken);

        return new RegisterResponse(saved.getId(), saved.getUsername(), saved.getEmail());
    }

    /**
     * Verifies a user's email address using the signed JWT from the verification email.
     *
     * @throws ResponseStatusException 400 if the token is expired, invalid, or already used
     */
    @Transactional
    public void verifyEmail(String token) {
        if (!jwtTokenProvider.validateEmailVerificationToken(token)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_OR_EXPIRED_TOKEN");
        }
        var userId = jwtTokenProvider.extractUserIdFromEmailVerificationToken(token);
        var maybeUser = userRepository.findById(userId);
        UserEntity user = maybeUser.orElseThrow(
                () -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_OR_EXPIRED_TOKEN"));
        if (user.isEmailVerified()) {
            return;
        }
        user.setEmailVerified(true);
        userRepository.save(user);
        emailService.sendWelcomeEmail(user.getEmail(), user.getUsername());
    }
}
