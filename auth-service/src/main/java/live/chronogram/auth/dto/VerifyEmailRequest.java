package live.chronogram.auth.dto;

/**
 * Data Transfer Object for general email verification requests.
 */
public class VerifyEmailRequest {
    private String mobileNumber;
    private String email;
    /**
     * The OTP code received via email.
     */
    private String otp;

    /**
     * Optional token to bind the verification to a specific registration session.
     */
    private String registrationToken;

    public String getMobileNumber() {
        return mobileNumber;
    }

    public void setMobileNumber(String mobileNumber) {
        this.mobileNumber = mobileNumber;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getOtp() {
        return otp;
    }

    public void setOtp(String otp) {
        this.otp = otp;
    }

    public String getRegistrationToken() {
        return registrationToken;
    }

    public void setRegistrationToken(String registrationToken) {
        this.registrationToken = registrationToken;
    }
}
