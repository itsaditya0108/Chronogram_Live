package live.chronogram.auth.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "sync_status")
public class SyncStatus {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "last_sync_at")
    private LocalDateTime lastSyncAt;

    @Column(name = "pending_files")
    private Integer pendingFiles = 0;

    @Column(name = "failed_files")
    private Integer failedFiles = 0;

    @Column(name = "device_count")
    private Integer deviceCount = 0;

    public SyncStatus() {}

    public SyncStatus(Long userId) {
        this.userId = userId;
    }

    // Getters and Setters
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public LocalDateTime getLastSyncAt() { return lastSyncAt; }
    public void setLastSyncAt(LocalDateTime lastSyncAt) { this.lastSyncAt = lastSyncAt; }
    public Integer getPendingFiles() { return pendingFiles; }
    public void setPendingFiles(Integer pendingFiles) { this.pendingFiles = pendingFiles; }
    public Integer getFailedFiles() { return failedFiles; }
    public void setFailedFiles(Integer failedFiles) { this.failedFiles = failedFiles; }
    public Integer getDeviceCount() { return deviceCount; }
    public void setDeviceCount(Integer deviceCount) { this.deviceCount = deviceCount; }
}
