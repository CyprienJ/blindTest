package com.blindtest.error;

import com.blindtest.config.CorrelationIdFilter;
import com.blindtest.model.dto.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import org.springframework.context.MessageSource;

/**
 * Creates the common REST error body from a safe error code and current request context. The
 * message is translated from the {@code i18n/errors} bundle; the English text of
 * {@link ApiErrorCode} is the fallback.
 */
public final class RestErrorResponseFactory {

    private RestErrorResponseFactory() {
    }

    /** Builds a response using only controlled messages and the request's correlation context. */
    public static ApiErrorResponse create(
            HttpServletRequest request,
            ApiErrorCode errorCode,
            Map<String, String> fieldErrors,
            MessageSource messages,
            Locale locale) {
        return new ApiErrorResponse(
                Instant.now(),
                errorCode.getStatus().value(),
                errorCode.name(),
                messages.getMessage("error." + errorCode.name(), null, errorCode.getMessage(), locale),
                request.getRequestURI(),
                CorrelationIdFilter.getRequestId(request),
                fieldErrors);
    }
}
