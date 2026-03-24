package live.chronogram.auth.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "storage_usage")
public class StorageUsage {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "photo_bytes")
    private Long photoBytes = 0L;

    @Column(name = "video_bytes")
    private Long videoBytes = 0L;

    @Column(name = "total_bytes")
    private Long totalBytes = 0L;

    @UpdateTimestamp
    @Column(name = "last_updated")
    private LocalDateTime lastUpdated;

    public StorageUsage() {}

    public StorageUsage(Long userId) {
        this.userId = userId;
    }

    // Getters and Setters
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getPhotoBytes() { return photoBytes; }
    public void setPhotoBytes(Long photoBytes) { this.photoBytes = photoBytes; }
    public Long getVideoBytes() { return videoBytes; }
    public void setVideoBytes(Long videoBytes) { this.videoBytes = videoBytes; }
    public Long getTotalBytes() { return totalBytes; }
    public void setTotalBytes(Long totalBytes) { this.totalBytes = totalBytes; }
    public LocalDateTime getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(LocalDateTime lastUpdated) { this.lastUpdated = lastUpdated; }
}
