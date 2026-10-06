package com.blindtest.security;

import com.blindtest.error.ApiErrorCode;
import com.blindtest.error.RestSecurityErrorHandler;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Servlet filter that extracts a Bearer JWT from the {@code Authorization} header,
 * validates it as an account or guest token, and populates the {@link SecurityContextHolder} for valid tokens.
 * Requests with a present but invalid or expired token receive a 401 response immediately.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final BearerTokenAuthenticator authenticator;
    private final RestSecurityErrorHandler restSecurityErrorHandler;

    /** Constructs the filter with the given account and guest token authenticator. */
    public JwtAuthFilter(
            BearerTokenAuthenticator authenticator,
            RestSecurityErrorHandler restSecurityErrorHandler) {
        this.authenticator = authenticator;
        this.restSecurityErrorHandler = restSecurityErrorHandler;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            applyBearerAuthentication(request, response, filterChain, authHeader);
        } else {
            filterChain.doFilter(request, response);
        }
    }

    private void applyBearerAuthentication(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain, String authHeader) throws IOException, ServletException {
        String token = authHeader.substring(BEARER_PREFIX.length());
        Optional<UsernamePasswordAuthenticationToken> authentication =
                authenticator.authenticate(token);
        if (authentication.isEmpty()) {
            restSecurityErrorHandler.write(request, response, ApiErrorCode.INVALID_ACCESS_TOKEN);
        } else {
            SecurityContextHolder.getContext().setAuthentication(authentication.get());
            filterChain.doFilter(request, response);
        }
    }
}
