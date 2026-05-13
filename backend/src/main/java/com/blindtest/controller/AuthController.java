package com.blindtest.controller;

import com.blindtest.model.dto.RegisterRequest;
import com.blindtest.model.dto.RegisterResponse;
import com.blindtest.model.dto.VerifyEmailRequest;
import com.blindtest.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** REST endpoints for authentication flows. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    /** Constructs the controller with the required service. */
    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Registers a new user account.
     *
     * <p>Validates the Cloudflare Turnstile token, checks for duplicates, hashes
     * the password, and sends an email-verification link. Returns 403 on Turnstile
     * failure and 409 on duplicate email or username.
     */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
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
}
