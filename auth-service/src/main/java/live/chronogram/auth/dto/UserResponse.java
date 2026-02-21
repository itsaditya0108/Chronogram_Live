package live.chronogram.auth.dto;

public class UserResponse {
    private Long userId;
    private String name;
    private String email;
    private String mobileNumber;
    private String dob;
    private String profilePictureUrl;
    private boolean isMobileVerified;
    private boolean isEmailVerified;
    private String status;

    public UserResponse(Long userId, String name, String email, String mobileNumber, String dob,
            String profilePictureUrl, boolean isMobileVerified, boolean isEmailVerified, String status) {
        this.userId = userId;
        this.name = name;
        this.email = email;
        this.mobileNumber = mobileNumber;
        this.dob = dob;
        this.profilePictureUrl = profilePictureUrl;
        this.isMobileVerified = isMobileVerified;
        this.isEmailVerified = isEmailVerified;
        this.status = status;
    }

    // Getters
    public Long getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getMobileNumber() {
        return mobileNumber;
    }

    public String getDob() {
        return dob;
    }

    public String getProfilePictureUrl() {
        return profilePictureUrl;
    }

    public boolean isMobileVerified() {
        return isMobileVerified;
    }

    public boolean isEmailVerified() {
        return isEmailVerified;
    }

    public String getStatus() {
        return status;
    }

    // Setters
    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setMobileNumber(String mobileNumber) {
        this.mobileNumber = mobileNumber;
    }

    public void setDob(String dob) {
        this.dob = dob;
    }

    public void setProfilePictureUrl(String profilePictureUrl) {
        this.profilePictureUrl = profilePictureUrl;
    }

    public void setMobileVerified(boolean mobileVerified) {
        isMobileVerified = mobileVerified;
    }

    public void setEmailVerified(boolean emailVerified) {
        isEmailVerified = emailVerified;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
