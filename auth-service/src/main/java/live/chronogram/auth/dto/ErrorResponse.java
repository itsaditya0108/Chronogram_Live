package live.chronogram.auth.dto;

import java.time.LocalDateTime;

/**
 * Standardized Error Response object returned by all API endpoints on failure.
 * Follows consistent structure for frontend error handling.
 */
public class ErrorResponse {
    /**
     * Server timestamp of the error.
     */
    private LocalDateTime timestamp;

    /**
     * HTTP status code (e.g., 400, 401, 403, 500).
     */
    private int status;

    /**
     * Short error keyword or title (e.g., "Bad Request", "Unauthorized").
     */
    private String error;

    /**
     * Detailed human-readable message explaining the error.
     */
    private String message;

    /**
     * Optional: partially masked email (e.g., a***@example.com) returned when
     * device approval is required, to guide the user to the correct inbox.
     */
    private String maskedEmail;

    /**
     * Optional: temporary token required to authorize specific follow-up actions
     * like resending a verification email after a 'Device Approval Required' error.
     */
    private String temporaryToken;

    /**
     * Optional: test OTP returned for development/testing convenience.
     */
    private String testOtp;

    /**
     * Unique Trace ID (Correlation ID) associated with this request/error.
     * Helps developers find the exact logs for this failure.
     */
    private String traceId;

    public ErrorResponse(int status, String error, String message) {
        this.timestamp = LocalDateTime.now();
        this.status = status;
        this.error = error;
        this.message = message;
    }

    public ErrorResponse(int status, String error, String message, String traceId) {
        this.timestamp = LocalDateTime.now();
        this.status = status;
        this.error = error;
        this.message = message;
        this.traceId = traceId;
    }

    public ErrorResponse(int status, String error, String message, String maskedEmail, String temporaryToken, String testOtp, String traceId) {
        this.timestamp = LocalDateTime.now();
        this.status = status;
        this.error = error;
        this.message = message;
        this.maskedEmail = maskedEmail;
        this.temporaryToken = temporaryToken;
        this.testOtp = testOtp;
        this.traceId = traceId;
    }

    // Getters
    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public int getStatus() {
        return status;
    }

    public String getError() {
        return error;
    }

    public String getMessage() {
        return message;
    }

    public String getMaskedEmail() {
        return maskedEmail;
    }

    public String getTemporaryToken() {
        return temporaryToken;
    }

    public String getTestOtp() {
        return testOtp;
    }

    public String getTraceId() {
        return traceId;
    }

    // Setters
    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public void setError(String error) {
        this.error = error;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public void setMaskedEmail(String maskedEmail) {
        this.maskedEmail = maskedEmail;
    }

    public void setTemporaryToken(String temporaryToken) {
        this.temporaryToken = temporaryToken;
    }

    public void setTestOtp(String testOtp) {
        this.testOtp = testOtp;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }
}
