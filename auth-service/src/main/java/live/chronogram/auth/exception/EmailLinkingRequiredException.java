package live.chronogram.auth.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when a user has authenticated via mobile but must link an
 * email
 * address before their registration is considered complete.
 */
public class EmailLinkingRequiredException extends AuthException {
    /**
     * Token authorizing the user to proceed with the email linking step.
     */
    private String registrationToken;

    public EmailLinkingRequiredException(String message) {
        super(HttpStatus.UNAUTHORIZED, message);
    }

    public EmailLinkingRequiredException(String message, String registrationToken) {
        super(HttpStatus.UNAUTHORIZED, message);
        this.registrationToken = registrationToken;
    }

    public String getRegistrationToken() {
        return registrationToken;
    }
}
