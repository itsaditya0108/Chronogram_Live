package live.chronogram.auth.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when a user attempts to log in from a new/untrusted device.
 * Requires the user to approve the device via an OTP sent to their verified
 * email.
 */
public class DeviceApprovalRequiredException extends AuthException {
    /**
     * Partially masked email address where the approval OTP was sent.
     */
    private final String maskedEmail;

    /**
     * Token required to authorize the 'resend-email-otp' action if needed.
     */
    private final String temporaryToken;

    /**
     * Optional: test OTP returned for development/testing convenience.
     */
    private final String testOtp;

    public DeviceApprovalRequiredException(String message, String maskedEmail, String temporaryToken, String testOtp) {
        super(HttpStatus.UNAUTHORIZED, message);
        this.maskedEmail = maskedEmail;
        this.temporaryToken = temporaryToken;
        this.testOtp = testOtp;
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
}
