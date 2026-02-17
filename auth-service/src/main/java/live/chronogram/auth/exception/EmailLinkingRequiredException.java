package live.chronogram.auth.exception;

public class EmailLinkingRequiredException extends RuntimeException {
    private String registrationToken;

    public EmailLinkingRequiredException(String message) {
        super(message);
    }

    public EmailLinkingRequiredException(String message, String registrationToken) {
        super(message);
        this.registrationToken = registrationToken;
    }

    public String getRegistrationToken() {
        return registrationToken;
    }
}
