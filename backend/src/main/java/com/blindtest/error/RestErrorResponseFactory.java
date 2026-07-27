package com.blindtest.error;

import com.blindtest.config.CorrelationIdFilter;
import com.blindtest.model.dto.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Map;

/** Creates the common REST error body from a safe error code and current request context. */
public final class RestErrorResponseFactory {

    private RestErrorResponseFactory() {
    }

    /** Builds a response using only controlled messages and the request's correlation context. */
    public static ApiErrorResponse create(
            HttpServletRequest request,
            ApiErrorCode errorCode,
            Map<String, String> fieldErrors) {
        return new ApiErrorResponse(
                Instant.now(),
                errorCode.getStatus().value(),
                errorCode.name(),
                errorCode.getMessage(),
                request.getRequestURI(),
                CorrelationIdFilter.getRequestId(request),
                fieldErrors);
    }
}
