package com.blindtest.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Servlet filter that extracts a Bearer JWT from the {@code Authorization} header,
 * validates it, and populates the {@link SecurityContextHolder} for valid tokens.
 * Requests with a present but invalid or expired token receive a 401 response immediately.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;

    /** Constructs the filter with the given token provider. */
    public JwtAuthFilter(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
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
        if (!jwtTokenProvider.validateToken(token)) {
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "Invalid or expired token");
        } else {
            UUID userId = jwtTokenProvider.extractUserId(token);
            String roleName = jwtTokenProvider.extractRole(token).name();
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    userId, null, List.of(new SimpleGrantedAuthority("ROLE_" + roleName)));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        }
    }
}
