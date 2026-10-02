package com.blindtest.controller;

import com.blindtest.config.properties.ApplicationProperties;
import com.blindtest.model.dto.AuthResult;
import com.blindtest.model.dto.ForgotPasswordRequest;
import com.blindtest.model.dto.InvitationLookupRequest;
import com.blindtest.model.dto.InvitationLookupResponse;
import com.blindtest.model.dto.LoginRequest;
import com.blindtest.model.dto.LoginResponse;
import com.blindtest.model.dto.RefreshResponse;
import com.blindtest.model.dto.RegisterRequest;
import com.blindtest.model.dto.RegisterResponse;
import com.blindtest.model.dto.ResetPasswordRequest;
import com.blindtest.model.dto.VerifyEmailRequest;
import com.blindtest.service.AuthService;
import com.blindtest.service.InvitationService;
import com.blindtest.service.RegistrationService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** REST endpoints for authentication flows. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String REFRESH_COOKIE_NAME = "refreshToken";
    private static final String COOKIE_PATH = "/api/auth";

    private final AuthService authService;
    private final InvitationService invitationService;
    private final RegistrationService registrationService;
    private final long refreshTokenTtlSeconds;
    private final boolean secureCookies;

    /** Constructs the controller with the required service and refresh-token TTL. */
    public AuthController(
            AuthService authService,
            InvitationService invitationService,
            RegistrationService registrationService,
            ApplicationProperties applicationProperties,
            @Value("${jwt.refresh-token.ttl-ms:604800000}") long refreshTokenTtlMs) {
        this.authService = authService;
        this.invitationService = invitationService;
        this.registrationService = registrationService;
        this.refreshTokenTtlSeconds = refreshTokenTtlMs / 1000;
        // Secure cookies require HTTPS. Production always serves over HTTPS
        // (ProductionConfigurationValidator enforces it), so this stays true there;
        // it follows app.base-url so a plain-HTTP environment (local development, the
        // isolated e2e Compose stack) still receives a cookie the browser will store.
        this.secureCookies = "https".equalsIgnoreCase(applicationProperties.baseUrl().getScheme());
    }

    /**
     * Returns the invited address behind a registration link so the form can display it.
     * Uses POST so the token stays out of access logs. Returns 400 if the invitation is
     * unknown, used, revoked, or expired.
     */
    @PostMapping("/invitations/lookup")
    public InvitationLookupResponse lookupInvitation(@Valid @RequestBody InvitationLookupRequest request) {
        return invitationService.lookup(request.token());
    }

    /**
     * Creates an account from an invitation; registration is invitation-only.
     *
     * <p>Validates the Cloudflare Turnstile token, consumes the invitation, and creates an
     * already verified account for the invited address. Returns 403 on Turnstile failure,
     * 400 on an unusable invitation, and 409 on a duplicate email or username.
     */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
        return registrationService.register(request);
    }

    /**
     * Verifies the user's email address using the signed JWT from the verification email.
     * Returns 400 if the token is expired or invalid.
     */
    @PostMapping("/verify-email")
    @ResponseStatus(HttpStatus.OK)
    public void verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        authService.verifyEmail(request.getToken());
    }

    /**
     * Authenticates the user and issues an access token plus an HttpOnly refresh cookie.
     * Returns 401 on wrong credentials, 403 if the email is not yet verified.
     */
    @PostMapping("/login")
    public LoginResponse login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response) {
        AuthResult result = authService.login(request);
        setRefreshCookie(response, result.getRefreshToken(), refreshTokenTtlSeconds);
        return new LoginResponse(result.getAccessToken(), result.getUser());
    }

    /**
     * Issues a new access token from a valid HttpOnly refresh cookie (token rotation).
     * Returns 401 if the cookie is absent or the token is unknown/expired.
     */
    @PostMapping("/refresh")
    public RefreshResponse refresh(
            @CookieValue(name = REFRESH_COOKIE_NAME, required = false) String refreshToken,
            HttpServletResponse response) {
        if (refreshToken == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN");
        }
        AuthResult result = authService.refresh(refreshToken);
        setRefreshCookie(response, result.getRefreshToken(), refreshTokenTtlSeconds);
        return new RefreshResponse(result.getAccessToken());
    }

    /**
     * Invalidates the refresh token and clears the HttpOnly cookie.
     * Succeeds even when the cookie is absent (idempotent).
     */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.OK)
    public void logout(
            @CookieValue(name = REFRESH_COOKIE_NAME, required = false) String refreshToken,
            HttpServletResponse response) {
        if (refreshToken != null) {
            authService.logout(refreshToken);
        }
        setRefreshCookie(response, "", 0);
    }

    /**
     * Sends a password-reset email if the address belongs to a registered account.
     * Always returns 200 to prevent email enumeration.
     */
    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.OK)
    public void forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request.getEmail());
    }

    /**
     * Changes the user's password using the signed reset token from the email link.
     * Returns 400 if the token is invalid or expired.
     */
    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.OK)
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.getToken(), request.getNewPassword());
    }

    private void setRefreshCookie(HttpServletResponse response, String value, long maxAgeSeconds) {
        var builder = ResponseCookie.from(REFRESH_COOKIE_NAME, value).httpOnly(true).secure(secureCookies);
        ResponseCookie cookie = builder.path(COOKIE_PATH).maxAge(maxAgeSeconds).sameSite("Strict").build();
        response.addHeader("Set-Cookie", cookie.toString());
    }
}
