package live.chronogram.auth.dto;

/**
 * Data Transfer Object for verifying email during the registration process.
 */
public class VerifyEmailRegistrationRequest {
    private String email;
    /**
     * The 6-digit OTP code sent to the email address.
     */
    private String otpCode;

    /**
     * Token used to bind this email verification to the ongoing registration
     * session.
     */
    private String registrationToken;

    // Getters and Setters
    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getOtpCode() {
        return otpCode;
    }

    public void setOtpCode(String otpCode) {
        this.otpCode = otpCode;
    }

    public String getRegistrationToken() {
        return registrationToken;
    }

    public void setRegistrationToken(String registrationToken) {
        this.registrationToken = registrationToken;
    }
}
