package live.chronogram.auth.exception;

public class DeviceApprovalRequiredException extends RuntimeException {
    private final String maskedEmail;

    public DeviceApprovalRequiredException(String message, String maskedEmail) {
        super(message);
        this.maskedEmail = maskedEmail;
    }

    public String getMaskedEmail() {
        return maskedEmail;
    }
}
