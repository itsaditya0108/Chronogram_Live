package live.chronogram.auth.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Entity representing a Lookup for User Account Statuses.
 * Mapping of 2-letter codes (e.g., 'AC') to descriptive names (e.g., 'ACTIVE').
 */
@Entity
@Table(name = "user_status")
public class UserStatus {

    /**
     * Unique 2-letter identifier for the status (e.g., AC=ACTIVE, SU=SUSPENDED,
     * BA=BANNED).
     */
    @Id
    @Column(name = "user_status_id", length = 10)
    private String userStatusId;

    /**
     * Human-readable name of the status.
     */
    @Column(nullable = false, length = 50)
    private String name;

    /**
     * Audit field: recorded when the status entry was first created.
     */
    @CreationTimestamp
    @Column(name = "created_timestamp", updatable = false)
    private LocalDateTime createdTimestamp;

    public UserStatus() {
    }

    public UserStatus(String userStatusId, String name, LocalDateTime createdTimestamp) {
        this.userStatusId = userStatusId;
        this.name = name;
        this.createdTimestamp = createdTimestamp;
    }

    public String getUserStatusId() {
        return userStatusId;
    }

    public void setUserStatusId(String userStatusId) {
        this.userStatusId = userStatusId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public LocalDateTime getCreatedTimestamp() {
        return createdTimestamp;
    }

    public void setCreatedTimestamp(LocalDateTime createdTimestamp) {
        this.createdTimestamp = createdTimestamp;
    }
}
