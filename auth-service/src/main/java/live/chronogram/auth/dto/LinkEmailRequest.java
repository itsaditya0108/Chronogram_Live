package live.chronogram.auth.dto;

/**
 * Data Transfer Object for linking an email address to a mobile-authenticated
 * user.
 */
public class LinkEmailRequest {
    private String mobileNumber;

    /**
     * The email address to be linked.
     */
    private String email;

    /**
     * Token authorizing the email linking process (obtained after mobile
     * verification).
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

    public String getRegistrationToken() {
        return registrationToken;
    }

    public void setRegistrationToken(String registrationToken) {
        this.registrationToken = registrationToken;
    }
}
