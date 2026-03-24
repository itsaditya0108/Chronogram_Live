package live.chronogram.auth.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import live.chronogram.auth.enums.OtpType;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Entity representing a One-Time Password (OTP) verification attempt.
 * Used for storing generated codes, attempt counts, and lockout status.
 */
@Entity
@Table(name = "otp_verification", indexes = {
        @Index(name = "idx_target_type", columnList = "target, otp_type")
})
public class OtpVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "otp_verification_id")
    private Long otpVerificationId;

    /**
     * Optional link to User ID if the user is already registered.
     */
    @Column(name = "user_id")
    private Long userId;

    /**
     * Destination where the OTP was sent (e.g., mobile number or email).
     */
    @Column(nullable = false)
    private String target;

    /**
     * The generated OTP code (currently stored in plaintext for testing).
     */
    @Column(name = "otp_code", nullable = false, length = 255)
    private String otpCode;

    /**
     * Context of the OTP (e.g., MOBILE_LOGIN, EMAIL_VERIFICATION).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "otp_type", nullable = false)
    private OtpType otpType;

    /**
     * True if the OTP has been successfully verified.
     */
    private Boolean verified;

    /**
     * Number of failed verification attempts for this specific OTP record.
     */
    private Integer attempts;

    /**
     * Number of times this OTP was resent to the user.
     */
    @Column(name = "resend_count")
    private Integer resendCount = 0;

    /**
     * Timestamp until which the target is locked due to excessive attempts.
     */
    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    /**
     * Unique session ID for binding with OTP session tokens.
     */
    @Column(name = "session_id", length = 50)
    private String sessionId;

    /**
     * Expiration timestamp of the OTP code.
     */
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /**
     * Creation timestamp of the record.
     */
    @CreationTimestamp
    @Column(name = "created_timestamp", updatable = false)
    private LocalDateTime createdTimestamp;

    public OtpVerification() {
    }

    public Long getOtpVerificationId() {
        return otpVerificationId;
    }

    public void setOtpVerificationId(Long otpVerificationId) {
        this.otpVerificationId = otpVerificationId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public String getOtpCode() {
        return otpCode;
    }

    public void setOtpCode(String otpCode) {
        this.otpCode = otpCode;
    }

    public OtpType getOtpType() {
        return otpType;
    }

    public void setOtpType(OtpType otpType) {
        this.otpType = otpType;
    }

    public Boolean getVerified() {
        return verified;
    }

    public void setVerified(Boolean verified) {
        this.verified = verified;
    }

    public Integer getAttempts() {
        return attempts;
    }

    public void setAttempts(Integer attempts) {
        this.attempts = attempts;
    }

    public Integer getResendCount() {
        return resendCount;
    }

    public void setResendCount(Integer resendCount) {
        this.resendCount = resendCount;
    }

    public LocalDateTime getLockedUntil() {
        return lockedUntil;
    }

    public void setLockedUntil(LocalDateTime lockedUntil) {
        this.lockedUntil = lockedUntil;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public LocalDateTime getCreatedTimestamp() {
        return createdTimestamp;
    }

    public void setCreatedTimestamp(LocalDateTime createdTimestamp) {
        this.createdTimestamp = createdTimestamp;
    }
}
