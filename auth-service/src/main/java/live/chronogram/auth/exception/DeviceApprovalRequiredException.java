package live.chronogram.auth.exception;

public class DeviceApprovalRequiredException extends RuntimeException {
    private final String maskedEmail;
    private final String temporaryToken;

    public DeviceApprovalRequiredException(String message, String maskedEmail, String temporaryToken) {
        super(message);
        this.maskedEmail = maskedEmail;
        this.temporaryToken = temporaryToken;
    }

    public String getMaskedEmail() {
        return maskedEmail;
    }

    public String getTemporaryToken() {
        return temporaryToken;
    }
}
