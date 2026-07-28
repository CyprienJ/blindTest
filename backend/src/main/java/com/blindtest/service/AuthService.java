package com.blindtest.service;

import com.blindtest.model.dto.AuthResult;
import com.blindtest.model.dto.LoginRequest;
import com.blindtest.model.dto.RegisterRequest;
import com.blindtest.model.dto.RegisterResponse;
import com.blindtest.model.dto.UserSummary;
import com.blindtest.model.entity.RefreshTokenEntity;
import com.blindtest.model.entity.UserEntity;
import com.blindtest.repository.RefreshTokenRepository;
import com.blindtest.repository.UserRepository;
import com.blindtest.security.JwtTokenProvider;
import com.blindtest.service.port.EmailSender;
import com.blindtest.service.port.TurnstileVerifier;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Business logic for user registration, email verification, and session authentication. */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final TurnstileVerifier turnstileVerifier;
    private final JwtTokenProvider jwtTokenProvider;
    private final EmailSender emailSender;
    private final Clock clock;
    private final long refreshTokenTtlMs;

    /** Constructs the service with all required dependencies. */
    public AuthService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            TurnstileVerifier turnstileVerifier,
            JwtTokenProvider jwtTokenProvider,
            EmailSender emailSender,
            Clock clock,
            @Value("${jwt.refresh-token.ttl-ms:604800000}") long refreshTokenTtlMs) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.turnstileVerifier = turnstileVerifier;
        this.jwtTokenProvider = jwtTokenProvider;
        this.emailSender = emailSender;
        this.clock = clock;
        this.refreshTokenTtlMs = refreshTokenTtlMs;
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
        if (!turnstileVerifier.verify(request.getTurnstileToken())) {
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
        emailSender.sendVerificationEmail(saved.getEmail(), saved.getUsername(), verificationToken);

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
        emailSender.sendWelcomeEmail(user.getEmail(), user.getUsername());
    }

    /**
     * Authenticates a user and issues tokens.
     *
     * @throws ResponseStatusException 401 if credentials are wrong
     * @throws ResponseStatusException 403 if the email is not yet verified
     */
    @Transactional
    public AuthResult login(LoginRequest request) {
        UserEntity user = userRepository.findByEmail(request.getEmail()).
                orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS"));
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS");
        }
        if (!user.isEmailVerified()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "EMAIL_NOT_VERIFIED");
        }
        user.setLastConnexion(LocalDateTime.now(clock));
        userRepository.save(user);

        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getRole());
        String refreshToken = jwtTokenProvider.generateRefreshToken();
        persistRefreshToken(user, refreshToken);

        UserSummary userSummary = new UserSummary(
                user.getId(), user.getUsername(), user.getEmail(),
                user.getRole().name(), user.getAvatarUrl());
        return new AuthResult(accessToken, refreshToken, userSummary);
    }

    /**
     * Rotates the refresh token and issues a new access token.
     *
     * @throws ResponseStatusException 401 if the token is unknown or expired
     */
    @Transactional
    public AuthResult refresh(String tokenValue) {
        RefreshTokenEntity stored = refreshTokenRepository.findByToken(tokenValue).
                orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN"));
        if (stored.getExpiresAt().isBefore(LocalDateTime.now(clock))) {
            refreshTokenRepository.delete(stored);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN");
        }
        UserEntity user = stored.getUser();
        refreshTokenRepository.delete(stored);

        String newAccessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getRole());
        String newRefreshToken = jwtTokenProvider.generateRefreshToken();
        persistRefreshToken(user, newRefreshToken);

        return new AuthResult(newAccessToken, newRefreshToken, null);
    }

    /** Invalidates the given refresh token. Silently succeeds if the token is unknown. */
    @Transactional
    public void logout(String tokenValue) {
        refreshTokenRepository.findByToken(tokenValue).ifPresent(refreshTokenRepository::delete);
    }

    /**
     * Sends a password-reset email if the address is known.
     * Always returns normally to avoid leaking whether an address is registered.
     */
    public void forgotPassword(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            String resetToken = jwtTokenProvider.generatePasswordResetToken(user.getId());
            emailSender.sendPasswordResetEmail(user.getEmail(), user.getUsername(), resetToken);
        });
    }

    /**
     * Changes a user's password using a valid password-reset token.
     *
     * @throws ResponseStatusException 400 if the token is invalid or expired
     */
    @Transactional
    public void resetPassword(String token, String newPassword) {
        if (!jwtTokenProvider.validatePasswordResetToken(token)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_OR_EXPIRED_TOKEN");
        }
        UUID userId = jwtTokenProvider.extractUserIdFromPasswordResetToken(token);
        UserEntity user = userRepository.findById(userId).
                orElseThrow(
                        () -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_OR_EXPIRED_TOKEN"));
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    private void persistRefreshToken(UserEntity user, String tokenValue) {
        RefreshTokenEntity token = new RefreshTokenEntity();
        token.setUser(user);
        token.setToken(tokenValue);
        token.setExpiresAt(LocalDateTime.now(clock).plusSeconds(refreshTokenTtlMs / 1000));
        refreshTokenRepository.save(token);
    }
}
