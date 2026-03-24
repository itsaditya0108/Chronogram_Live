package live.chronogram.auth.dto;

/**
 * Data Transfer Object representing a User's public/profile information.
 * Typically returned after successful login or profile retrieval.
 */
public class UserResponse {
    private Long userId;       // Internal database ID
    private String name;       // Full name
    /**
     * Masked Email (e.g., "a***a@example.com").
     * Logic: Masked in AuthService.getUserDetails() for PII protection.
     */
    private String email;      
    /**
     * Masked Mobile (e.g., "******1234").
     * Logic: Masked in AuthService.getUserDetails() for PII protection.
     */
    private String mobileNumber; 
    private String dob;        // Birthdate

    private boolean isMobileVerified;
    private boolean isEmailVerified;
    /**
     * Current account status.
     * Logic: Controlled by UserStatus entity.
     */
    private String status;

    public UserResponse(Long userId, String name, String email, String mobileNumber, String dob,
            boolean isMobileVerified, boolean isEmailVerified, String status) {
        this.userId = userId;
        this.name = name;
        this.email = email;
        this.mobileNumber = mobileNumber;
        this.dob = dob;
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
