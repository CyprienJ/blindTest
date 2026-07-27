package com.blindtest.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Assigns a safe request ID to every HTTP request and exposes it through MDC and response headers. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Request-ID";
    public static final String ATTRIBUTE_NAME = CorrelationIdFilter.class.getName() + ".requestId";
    public static final String MDC_KEY = "requestId";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String requestId = resolveRequestId(request.getHeader(HEADER_NAME));
        request.setAttribute(ATTRIBUTE_NAME, requestId);
        response.setHeader(HEADER_NAME, requestId);
        MDC.put(MDC_KEY, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    /** Returns the request ID assigned by this filter, generating one only as a defensive fallback. */
    public static String getRequestId(HttpServletRequest request) {
        Object requestId = request.getAttribute(ATTRIBUTE_NAME);
        if (requestId instanceof String value) {
            return value;
        }
        return UUID.randomUUID().toString();
    }

    private String resolveRequestId(String candidate) {
        if (candidate != null) {
            try {
                return UUID.fromString(candidate).toString();
            } catch (IllegalArgumentException ignored) {
                // Untrusted or malformed IDs are replaced to keep logs safe and consistently searchable.
            }
        }
        return UUID.randomUUID().toString();
    }
}
