package live.chronogram.auth.exception;

import live.chronogram.auth.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

/**
 * Centralized Exception Handling for the entire Auth Service.
 * Maps custom and standard exceptions to meaningful {@link ErrorResponse}
 * objects with appropriate HTTP statuses.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Extracts the current transaction/trace ID from MDC.
     */
    private String getTraceId() {
        return org.slf4j.MDC.get("txId");
    }

    /**
     * Handles cases where a login attempt from a new device requires email
     * approval.
     * Returns 401 Unauthorized with a masked email and temporary token.
     */
    @ExceptionHandler(DeviceApprovalRequiredException.class)
    public ResponseEntity<ErrorResponse> handleDeviceApproval(DeviceApprovalRequiredException ex) {
        ErrorResponse error = new ErrorResponse(
                HttpStatus.UNAUTHORIZED.value(),
                ex.getMessage(),
                "Unauthorized",
                ex.getMaskedEmail(),
                ex.getTemporaryToken(),
                getTraceId());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }

    /**
     * Explicitly handles Spring Security's Access Denied exceptions to return 403.
     */
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(org.springframework.security.access.AccessDeniedException ex) {
        ErrorResponse error = new ErrorResponse(
                HttpStatus.FORBIDDEN.value(),
                ex.getMessage(),
                "Forbidden",
                getTraceId());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    /**
     * Handles registration flows where an email must be linked before proceeding.
     * Returns 403 Forbidden.
     */
    @ExceptionHandler(EmailLinkingRequiredException.class)
    public ResponseEntity<ErrorResponse> handleEmailLinking(EmailLinkingRequiredException ex) {
        ErrorResponse error = new ErrorResponse(
                HttpStatus.FORBIDDEN.value(),
                ex.getMessage(),
                "Forbidden",
                getTraceId());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    /**
     * Generic handler for RuntimeExceptions, usually mapped to 400 Bad Request.
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntimeException(RuntimeException ex) {
        logger.error("[400] RuntimeException: {}", ex.getMessage());
        ErrorResponse error = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                ex.getMessage(),
                "Bad Request",
                getTraceId());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * Specialized handler for our custom {@link AuthException} which carries a
     * specific HttpStatus.
     */
    @ExceptionHandler(live.chronogram.auth.exception.AuthException.class)
    public ResponseEntity<ErrorResponse> handleAuthException(live.chronogram.auth.exception.AuthException ex,
            WebRequest request) {
        logger.warn("[{}] AuthException: {} | Detail: {}", ex.getStatus().value(), ex.getMessage(), request.getDescription(false));
        ErrorResponse errorDetails = new ErrorResponse(
                ex.getStatus().value(),
                ex.getStatus().getReasonPhrase(),
                ex.getMessage(),
                getTraceId());
        return new ResponseEntity<>(errorDetails, ex.getStatus());
    }

    /**
     * Handles malformed JSON payloads.
     */
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadable(org.springframework.http.converter.HttpMessageNotReadableException ex) {
        ErrorResponse error = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "Malformed JSON request",
                "Bad Request",
                getTraceId());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * Handles validation errors from @Valid annotated request bodies.
     */
    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationExceptions(org.springframework.web.bind.MethodArgumentNotValidException ex, WebRequest request) {
        StringBuilder errors = new StringBuilder();
        ex.getBindingResult().getFieldErrors().forEach(error -> {
            errors.append(error.getField()).append(": ").append(error.getDefaultMessage()).append("; ");
        });

        ErrorResponse errorDetails = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                errors.toString(),
                "Validation Error",
                getTraceId());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorDetails);
    }

    /**
     * Catch-all handler for any unhandled exceptions to prevent leaking internal
     * details.
     * Returns 500 Internal Server Error.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> globalExceptionHandler(Exception ex, WebRequest request) {
        // Log the full stack trace for 500 errors (MDC will ensure traceId is in logs)
        logger.error("[500] Unexpected error occurred during request: {}", request.getDescription(false), ex);

        ErrorResponse error = new ErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "An unexpected error occurred. Please contact support with Trace ID: " + getTraceId(),
                "Internal Server Error",
                getTraceId());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
