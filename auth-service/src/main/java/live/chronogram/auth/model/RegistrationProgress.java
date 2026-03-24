package live.chronogram.auth.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "registration_progress")
public class RegistrationProgress {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "otp_sent")
    private Boolean otpSent = false;

    @Column(name = "otp_verified")
    private Boolean otpVerified = false;

    @Column(name = "profile_created")
    private Boolean profileCreated = false;

    @Column(name = "device_registered")
    private Boolean deviceRegistered = false;

    @Column(name = "sync_enabled")
    private Boolean syncEnabled = false;

    @Column(name = "completed")
    private Boolean completed = false;

    @UpdateTimestamp
    @Column(name = "last_updated")
    private LocalDateTime lastUpdated;

    public RegistrationProgress() {}

    public RegistrationProgress(Long userId) {
        this.userId = userId;
    }

    // Getters and Setters
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Boolean getOtpSent() { return otpSent; }
    public void setOtpSent(Boolean otpSent) { this.otpSent = otpSent; }
    public Boolean getOtpVerified() { return otpVerified; }
    public void setOtpVerified(Boolean otpVerified) { this.otpVerified = otpVerified; }
    public Boolean getProfileCreated() { return profileCreated; }
    public void setProfileCreated(Boolean profileCreated) { this.profileCreated = profileCreated; }
    public Boolean getDeviceRegistered() { return deviceRegistered; }
    public void setDeviceRegistered(Boolean deviceRegistered) { this.deviceRegistered = deviceRegistered; }
    public Boolean getSyncEnabled() { return syncEnabled; }
    public void setSyncEnabled(Boolean syncEnabled) { this.syncEnabled = syncEnabled; }
    public Boolean getCompleted() { return completed; }
    public void setCompleted(Boolean completed) { this.completed = completed; }
    public LocalDateTime getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(LocalDateTime lastUpdated) { this.lastUpdated = lastUpdated; }
}
