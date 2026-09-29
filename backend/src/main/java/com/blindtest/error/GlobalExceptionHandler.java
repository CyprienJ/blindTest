package com.blindtest.error;

import com.blindtest.config.CorrelationIdFilter;
import com.blindtest.model.dto.ApiErrorResponse;
import com.blindtest.model.game.GameConfigurationValidationException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Converts controller, validation, domain, and unexpected exceptions to one safe REST contract. */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request) {
        return build(request, ApiErrorCode.VALIDATION_FAILED,
                fieldErrors(exception.getBindingResult().getFieldErrors()));
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiErrorResponse> handleBinding(
            BindException exception,
            HttpServletRequest request) {
        return build(request, ApiErrorCode.VALIDATION_FAILED,
                fieldErrors(exception.getBindingResult().getFieldErrors()));
    }

    /** Converts method-level constraint violations without exposing rejected values. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(
            ConstraintViolationException exception,
            HttpServletRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        exception.getConstraintViolations().forEach(violation ->
                errors.putIfAbsent(violation.getPropertyPath().toString(), violation.getMessage()));
        return build(request, ApiErrorCode.VALIDATION_FAILED, errors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleMalformedBody(
            HttpMessageNotReadableException exception,
            HttpServletRequest request) {
        return build(request, ApiErrorCode.MALFORMED_REQUEST, Map.of());
    }

    @ExceptionHandler({
        MethodArgumentTypeMismatchException.class,
        MissingServletRequestParameterException.class,
        MissingServletRequestPartException.class
    })
    public ResponseEntity<ApiErrorResponse> handleInvalidParameter(
            Exception exception,
            HttpServletRequest request) {
        return build(request, ApiErrorCode.INVALID_PARAMETER, Map.of());
    }

    /** Reports multipart bodies rejected by the servlet size limits before any controller runs. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleUploadTooLarge(
            MaxUploadSizeExceededException exception,
            HttpServletRequest request) {
        return build(request, ApiErrorCode.IMPORT_FILE_TOO_LARGE, Map.of());
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiErrorResponse> handleMalformedMultipart(
            MultipartException exception,
            HttpServletRequest request) {
        return build(request, ApiErrorCode.MALFORMED_REQUEST, Map.of());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatus(
            ResponseStatusException exception,
            HttpServletRequest request) {
        ApiErrorCode errorCode = ApiErrorCode.from(exception.getStatusCode(), exception.getReason());
        return build(request, errorCode, Map.of());
    }

    /** Preserves the game domain's stable configuration code at the REST boundary. */
    @ExceptionHandler(GameConfigurationValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleGameConfiguration(
            GameConfigurationValidationException exception,
            HttpServletRequest request) {
        return build(request, ApiErrorCode.valueOf(exception.code()), Map.of());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(
            NoResourceFoundException exception,
            HttpServletRequest request) {
        return build(request, ApiErrorCode.RESOURCE_NOT_FOUND, Map.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException exception,
            HttpServletRequest request) {
        return build(request, ApiErrorCode.METHOD_NOT_ALLOWED, Map.of());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleUnsupportedMediaType(
            HttpMediaTypeNotSupportedException exception,
            HttpServletRequest request) {
        return build(request, ApiErrorCode.UNSUPPORTED_MEDIA_TYPE, Map.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConflict(
            DataIntegrityViolationException exception,
            HttpServletRequest request) {
        return build(request, ApiErrorCode.CONFLICT, Map.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(
            AccessDeniedException exception,
            HttpServletRequest request) {
        return build(request, ApiErrorCode.ACCESS_DENIED, Map.of());
    }

    /** Logs unexpected failures with their request ID and returns a generic client-safe response. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(
            Exception exception,
            HttpServletRequest request) {
        String requestId = CorrelationIdFilter.getRequestId(request);
        log.error("Unhandled request failure requestId={} method={} path={}",
                requestId, request.getMethod(), request.getRequestURI(), exception);
        return build(request, ApiErrorCode.INTERNAL_ERROR, Map.of());
    }

    private ResponseEntity<ApiErrorResponse> build(
            HttpServletRequest request,
            ApiErrorCode errorCode,
            Map<String, String> fieldErrors) {
        ApiErrorResponse response = RestErrorResponseFactory.create(request, errorCode, fieldErrors);
        return ResponseEntity.status(errorCode.getStatus()).body(response);
    }

    private Map<String, String> fieldErrors(Iterable<FieldError> fieldErrors) {
        Map<String, String> errors = new LinkedHashMap<>();
        fieldErrors.forEach(error ->
                errors.putIfAbsent(error.getField(), safeValidationMessage(error)));
        return errors;
    }

    private String safeValidationMessage(FieldError error) {
        String message = error.getDefaultMessage();
        return message == null ? "invalid value" : message;
    }
}
