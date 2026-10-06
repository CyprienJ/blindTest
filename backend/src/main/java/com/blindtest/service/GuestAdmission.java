package com.blindtest.service;

import com.blindtest.error.ApiErrorCode;
import com.blindtest.model.dto.GuestJoinRequest;
import com.blindtest.security.JwtTokenProvider;
import com.blindtest.service.port.TurnstileVerifier;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Checks a guest join request before it reaches a lobby (ADR-0020) and issues the guest token
 * once the lobby admitted it. Rate limits apply before Turnstile is called.
 */
@Component
public class GuestAdmission {

    private final GuestJoinRateLimiter rateLimiter;
    private final TurnstileVerifier turnstileVerifier;
    private final JwtTokenProvider jwtTokenProvider;

    /** Wires the join limits, the Turnstile verifier, and the token issuer. */
    public GuestAdmission(
            GuestJoinRateLimiter rateLimiter,
            TurnstileVerifier turnstileVerifier,
            JwtTokenProvider jwtTokenProvider) {
        this.rateLimiter = rateLimiter;
        this.turnstileVerifier = turnstileVerifier;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    /** Returns the normalized nickname, or fails with a stable error code. */
    public String check(String clientAddress, String limitedCode, GuestJoinRequest request) {
        if (!rateLimiter.tryAcquire(clientAddress, limitedCode)) {
            throw failure(ApiErrorCode.GUEST_JOIN_RATE_LIMITED);
        }
        String nickname = GuestNickname.normalize(request == null ? null : request.nickname()).
                orElseThrow(() -> failure(ApiErrorCode.GUEST_NICKNAME_INVALID));
        String turnstileToken = request.turnstileToken();
        if (turnstileToken == null || turnstileToken.isBlank()
                || !turnstileVerifier.verify(turnstileToken)) {
            throw failure(ApiErrorCode.TURNSTILE_FAILED);
        }
        return nickname;
    }

    /** Issues the token scoped to one participant of one session. */
    public String issueToken(UUID sessionId, UUID participantId) {
        return jwtTokenProvider.generateGuestToken(sessionId, participantId);
    }

    private static ResponseStatusException failure(ApiErrorCode code) {
        return new ResponseStatusException(code.getStatus(), code.name());
    }
}
