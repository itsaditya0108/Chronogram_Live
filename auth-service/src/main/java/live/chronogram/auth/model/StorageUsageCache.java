package live.chronogram.auth.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "storage_usage_cache")
public class StorageUsageCache {

    @Id
    @Column(name = "user_id")
    private Long userId;

    private Double photos; // Usage in GB
    private Double videos; // Usage in GB

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public StorageUsageCache() {
        this.photos = 0.0;
        this.videos = 0.0;
    }

    public StorageUsageCache(Long userId, Double photos, Double videos) {
        this.userId = userId;
        this.photos = photos;
        this.videos = videos;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Double getPhotos() {
        return photos != null ? photos : 0.0;
    }

    public void setPhotos(Double photos) {
        this.photos = photos;
    }

    public Double getVideos() {
        return videos != null ? videos : 0.0;
    }

    public void setVideos(Double videos) {
        this.videos = videos;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
