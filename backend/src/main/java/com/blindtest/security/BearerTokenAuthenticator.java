package com.blindtest.security;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

/**
 * Turns a bearer token into an authentication shared by the HTTP and STOMP paths. Accounts
 * authenticate as their account ID with their role; guests (ADR-0020) authenticate as their
 * participant ID with {@code ROLE_GUEST} and the verified {@link GuestToken} as details.
 */
@Component
public class BearerTokenAuthenticator {

    /** Authority granted to guest participant tokens. */
    public static final String GUEST_AUTHORITY = "ROLE_GUEST";

    private final JwtTokenProvider jwtTokenProvider;

    /** Wires the token provider. */
    public BearerTokenAuthenticator(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    /** Returns the authentication for a valid account or guest token, or empty otherwise. */
    public Optional<UsernamePasswordAuthenticationToken> authenticate(String token) {
        if (jwtTokenProvider.validateToken(token)) {
            UUID userId = jwtTokenProvider.extractUserId(token);
            String role = jwtTokenProvider.extractRole(token).name();
            return Optional.of(new UsernamePasswordAuthenticationToken(
                    userId, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
        }
        return jwtTokenProvider.parseGuestToken(token).map(guest -> {
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            guest.participantId(), null,
                            List.of(new SimpleGrantedAuthority(GUEST_AUTHORITY)));
            authentication.setDetails(guest);
            return authentication;
        });
    }
}
