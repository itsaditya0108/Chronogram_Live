package live.chronogram.auth.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Entity representing a physical or virtual Device associated with a User.
 * Tracks hardware identifiers, OS/App versions, and trust status for security
 * purposes.
 */
@Entity
@Table(name = "user_devices")
public class UserDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_device_id")
    private Long userDeviceId;

    /**
     * The User who owns/uses this device.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * Unique hardware or software identifier (e.g., Android ID, IDFV).
     */
    @Column(name = "device_id", nullable = false)
    private String deviceId;

    /**
     * Human-readable name given to the device (e.g., "My iPhone").
     */
    @Column(name = "device_name", length = 100)
    private String deviceName;

    /**
     * Specific hardware model (e.g., "iPhone 15 Pro", "Pixel 8").
     */
    @Column(name = "device_model", length = 100)
    private String deviceModel;

    @Column(name = "os_name", length = 50)
    private String osName;

    @Column(name = "os_version", length = 50)
    private String osVersion;

    @Column(name = "app_version", length = 50)
    private String appVersion;

    /**
     * Indicates if the device is trusted by the user (usually after successul 2FA).
     * Trusted devices may bypass certain security prompts.
     */
    @Column(name = "is_trusted")
    private Boolean isTrusted;

    /**
     * Hex-encoded SHA-256 hash of the SIM serial number for strict binding.
     */
    @Column(name = "sim_serial_hash", length = 255)
    private String simSerialHash;

    /**
     * Firebase Cloud Messaging (FCM) or APNs token for push notifications.
     */
    @Column(name = "push_token", columnDefinition = "TEXT")
    private String pushToken;

    /**
     * Audit field: recorded when the app was first installed/registered on this
     * device.
     */
    @CreationTimestamp
    @Column(name = "first_install_timestamp", updatable = false)
    private LocalDateTime firstInstallTimestamp;

    /**
     * Audit field: updated every time the user logs in from this device.
     */
    @UpdateTimestamp
    @Column(name = "last_login_timestamp")
    private LocalDateTime lastLoginTimestamp;

    /**
     * Last known latitude of the device during a security event (e.g., login).
     */
    @Column(name = "latitude")
    private Double latitude;

    /**
     * Last known longitude of the device during a security event.
     */
    @Column(name = "longitude")
    private Double longitude;

    @Column(name = "country", length = 100)
    private String country;

    @Column(name = "city", length = 100)
    private String city;

    public UserDevice() {
    }

    // Getters and Setters

    public Long getUserDeviceId() {
        return userDeviceId;
    }

    public void setUserDeviceId(Long userDeviceId) {
        this.userDeviceId = userDeviceId;
    }

    @JsonIgnore
    public User getUser() {
        return user;
    }

    @JsonIgnore
    public void setUser(User user) {
        this.user = user;
    }

    @JsonProperty("userId")
    public Long getUserId() {
        return user != null ? user.getUserId() : null;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public String getDeviceModel() {
        return deviceModel;
    }

    public void setDeviceModel(String deviceModel) {
        this.deviceModel = deviceModel;
    }

    public String getOsName() {
        return osName;
    }

    public void setOsName(String osName) {
        this.osName = osName;
    }

    public String getOsVersion() {
        return osVersion;
    }

    public void setOsVersion(String osVersion) {
        this.osVersion = osVersion;
    }

    public String getAppVersion() {
        return appVersion;
    }

    public void setAppVersion(String appVersion) {
        this.appVersion = appVersion;
    }

    public Boolean getIsTrusted() {
        return isTrusted;
    }

    public void setIsTrusted(Boolean isTrusted) {
        this.isTrusted = isTrusted;
    }

    public String getSimSerialHash() {
        return simSerialHash;
    }

    public void setSimSerialHash(String simSerialHash) {
        this.simSerialHash = simSerialHash;
    }

    public String getPushToken() {
        return pushToken;
    }

    public void setPushToken(String pushToken) {
        this.pushToken = pushToken;
    }

    public LocalDateTime getFirstInstallTimestamp() {
        return firstInstallTimestamp;
    }

    public void setFirstInstallTimestamp(LocalDateTime firstInstallTimestamp) {
        this.firstInstallTimestamp = firstInstallTimestamp;
    }

    public LocalDateTime getLastLoginTimestamp() {
        return lastLoginTimestamp;
    }

    public void setLastLoginTimestamp(LocalDateTime lastLoginTimestamp) {
        this.lastLoginTimestamp = lastLoginTimestamp;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }
}
