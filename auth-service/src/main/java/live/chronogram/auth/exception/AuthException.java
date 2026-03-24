package live.chronogram.auth.exception;

import org.springframework.http.HttpStatus;

/**
 * Custom base exception for Authentication and Authorization errors.
 * Encapsulates an {@link HttpStatus} to allow the
 * {@link GlobalExceptionHandler} to return correct codes.
 */
public class AuthException extends RuntimeException {
    /**
     * The HTTP status associated with this specific error (e.g., 401, 403, 404).
     */
    private final HttpStatus status;

    public AuthException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
