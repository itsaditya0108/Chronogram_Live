package live.chronogram.auth.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Entity representing an active Login Session for a User on a specific Device.
 * Primarily used for managing JWT Refresh Tokens and session revocation.
 */
@Entity
@Table(name = "user_sessions")
public class UserSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "session_id")
    private Long sessionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_device_id")
    private UserDevice userDevice;

    /**
     * Hex-encoded SHA-256 hash of the Refresh Token.
     * Stored as a hash for security in case of database breach.
     */
    @Column(name = "refresh_token_hash", nullable = false, unique = true)
    private String refreshTokenHash;

    /**
     * IP address from which the session started.
     */
    @Column(name = "ip_address")
    private String ipAddress;

    /**
     * Browser or App user agent string.
     */
    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    private String country;
    private String city;
    private Double latitude;
    private Double longitude;
    private Integer accuracy;

    /**
     * If true, the session has been revoked (logout or security breach)
     * and the refresh token is no longer valid.
     */
    @Column(name = "is_revoked")
    private Boolean isRevoked;

    /**
     * Timestamp when this session (refresh token) expires.
     */
    @Column(name = "expires_timestamp", nullable = false)
    private LocalDateTime expiresTimestamp;

    /**
     * Audit field: recorded when the session was created.
     */
    @CreationTimestamp
    @Column(name = "created_timestamp", updatable = false)
    private LocalDateTime createdTimestamp;

    public UserSession() {
    }

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public UserDevice getUserDevice() {
        return userDevice;
    }

    public void setUserDevice(UserDevice userDevice) {
        this.userDevice = userDevice;
    }

    public String getRefreshTokenHash() {
        return refreshTokenHash;
    }

    public void setRefreshTokenHash(String refreshTokenHash) {
        this.refreshTokenHash = refreshTokenHash;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
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

    public Integer getAccuracy() {
        return accuracy;
    }

    public void setAccuracy(Integer accuracy) {
        this.accuracy = accuracy;
    }

    public Boolean getIsRevoked() {
        return isRevoked;
    }

    public void setIsRevoked(Boolean isRevoked) {
        this.isRevoked = isRevoked;
    }

    public LocalDateTime getExpiresTimestamp() {
        return expiresTimestamp;
    }

    public void setExpiresTimestamp(LocalDateTime expiresTimestamp) {
        this.expiresTimestamp = expiresTimestamp;
    }

    public LocalDateTime getCreatedTimestamp() {
        return createdTimestamp;
    }

    public void setCreatedTimestamp(LocalDateTime createdTimestamp) {
        this.createdTimestamp = createdTimestamp;
    }
}
