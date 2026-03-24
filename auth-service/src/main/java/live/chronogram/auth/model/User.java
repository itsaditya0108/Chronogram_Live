package live.chronogram.auth.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import live.chronogram.auth.util.AttributeEncryptor;

/**
 * Entity representing a Registered User in the system.
 * Contains PII (Personally Identifiable Information) such as name, email, and
 * mobile number,
 * which are transparently encrypted at the database level using
 * {@link AttributeEncryptor}.
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long userId;

    /**
     * User's full name, encrypted in DB.
     */
    @Convert(converter = AttributeEncryptor.class)
    private String name;

    /**
     * Unique mobile number used for primary identification and OTP-based login.
     * Encrypted in DB.
     */
    @Convert(converter = AttributeEncryptor.class)
    @Column(name = "mobile_number", nullable = false, unique = true, length = 100)
    private String mobileNumber;

    /**
     * Unique email address, encrypted in DB.
     */
    @Convert(converter = AttributeEncryptor.class)
    @Column(unique = true)
    private String email;

    /**
     * BCRYPT hash of the user's password (if password-based login is enabled).
     */
    @Column(name = "password_hash")
    private String passwordHash;

    /**
     * Date of birth for age verification (12+ required).
     */
    private LocalDate dob;


    @Column(name = "email_verified")
    private Boolean emailVerified;

    @Column(name = "mobile_verified")
    private Boolean mobileVerified;

    /**
     * Internal registration progress step.
     */
    @Column(name = "registration_status", length = 50)
    private String registrationStatus = "OTP_SENT";

    /**
     * Admin approval status.
     */
    @Column(name = "approval_status", length = 50)
    private String approvalStatus = "PENDING";

    /**
     * Flag indicating if the user is explicitly blocked by an admin.
     */
    @Column(name = "is_blocked", nullable = false)
    private Boolean isBlocked = false;

    /**
     * Flag indicating if the user has soft-deleted their account.
     */
    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    /**
     * The ID of the admin who approved this user.
     */
    @Column(name = "approved_by")
    private Long approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "blocked_at")
    private LocalDateTime blockedAt;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /**
     * Current status of the user account (legacy - logic migrates to new flags).
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_status_id")
    private UserStatus userStatus;

    /**
     * Reason for the current status (especially if BLOCKED).
     */
    @Column(name = "status_reason", length = 500)
    private String statusReason;

    /**
     * Counter for consecutive failed login attempts to trigger account lockout.
     */
    @Column(name = "failed_login_attempts")
    private Integer failedLoginAttempts;

    /**
     * Timestamp until which the account is locked due to multiple failed attempts.
     */
    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    @Column(name = "reset_token", length = 100)
    private String resetToken;

    @Column(name = "reset_token_expiry")
    private LocalDateTime resetTokenExpiry;

    /**
     * Audit field: recorded when the user record was first created.
     */
    @CreationTimestamp
    @Column(name = "created_timestamp", updatable = false)
    private LocalDateTime createdTimestamp;

    /**
     * Audit field: recorded every time the user record is updated.
     */
    @UpdateTimestamp
    @Column(name = "updated_timestamp")
    private LocalDateTime updatedTimestamp;


    public User() {
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

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

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public LocalDate getDob() {
        return dob;
    }

    public void setDob(LocalDate dob) {
        this.dob = dob;
    }


    public Boolean getEmailVerified() {
        return emailVerified;
    }

    public void setEmailVerified(Boolean emailVerified) {
        this.emailVerified = emailVerified;
    }

    public Boolean getMobileVerified() {
        return mobileVerified;
    }

    public void setMobileVerified(Boolean mobileVerified) {
        this.mobileVerified = mobileVerified;
    }

    public UserStatus getUserStatus() {
        return userStatus;
    }

    public void setUserStatus(UserStatus userStatus) {
        this.userStatus = userStatus;
    }

    public String getStatusReason() {
        return statusReason;
    }

    public void setStatusReason(String statusReason) {
        this.statusReason = statusReason;
    }

    public Integer getFailedLoginAttempts() {
        return failedLoginAttempts;
    }

    public void setFailedLoginAttempts(Integer failedLoginAttempts) {
        this.failedLoginAttempts = failedLoginAttempts;
    }

    public LocalDateTime getLockedUntil() {
        return lockedUntil;
    }

    public void setLockedUntil(LocalDateTime lockedUntil) {
        this.lockedUntil = lockedUntil;
    }

    public String getResetToken() {
        return resetToken;
    }

    public void setResetToken(String resetToken) {
        this.resetToken = resetToken;
    }

    public LocalDateTime getResetTokenExpiry() {
        return resetTokenExpiry;
    }

    public void setResetTokenExpiry(LocalDateTime resetTokenExpiry) {
        this.resetTokenExpiry = resetTokenExpiry;
    }

    public LocalDateTime getCreatedTimestamp() {
        return createdTimestamp;
    }

    public void setCreatedTimestamp(LocalDateTime createdTimestamp) {
        this.createdTimestamp = createdTimestamp;
    }

    public LocalDateTime getUpdatedTimestamp() {
        return updatedTimestamp;
    }

    public void setUpdatedTimestamp(LocalDateTime updatedTimestamp) {
        this.updatedTimestamp = updatedTimestamp;
    }


    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }

    public String getRegistrationStatus() {
        return registrationStatus;
    }

    public void setRegistrationStatus(String registrationStatus) {
        this.registrationStatus = registrationStatus;
    }

    public String getApprovalStatus() {
        return approvalStatus;
    }

    public void setApprovalStatus(String approvalStatus) {
        this.approvalStatus = approvalStatus;
    }

    public Boolean getIsBlocked() {
        return isBlocked;
    }

    public void setIsBlocked(Boolean isBlocked) {
        this.isBlocked = isBlocked;
    }

    public Boolean getIsDeleted() {
        return isDeleted;
    }

    public void setIsDeleted(Boolean isDeleted) {
        this.isDeleted = isDeleted;
    }

    public Long getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(Long approvedBy) {
        this.approvedBy = approvedBy;
    }

    public LocalDateTime getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(LocalDateTime approvedAt) {
        this.approvedAt = approvedAt;
    }

    public LocalDateTime getBlockedAt() {
        return blockedAt;
    }

    public void setBlockedAt(LocalDateTime blockedAt) {
        this.blockedAt = blockedAt;
    }

    public LocalDateTime getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(LocalDateTime lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }
}
