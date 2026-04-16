package com.company.video_service.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "videos")
public class Video {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "video_id")
    private Long videoId;

    @Column(name = "video_uid", nullable = false, unique = true, length = 64)
    private String videoUid;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "upload_uid", nullable = false, unique = true, length = 64)
    private String uploadUid;

    @Column(name = "title", length = 255)
    private String title;

    @Column(name = "original_file_path", nullable = false, length = 500)
    private String originalFilePath;

    @Column(name = "original_file_size", nullable = false)
    private Long originalFileSize;

    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    @Column(name = "duration_seconds")
    private Long durationSeconds;

    @Column(name = "video_width")
    private Integer videoWidth;

    @Column(name = "video_height")
    private Integer videoHeight;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private VideoStatus status = VideoStatus.UPLOADED;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "deleted_timestamp")
    private LocalDateTime deletedTimestamp;

    @Column(name = "created_timestamp", nullable = false, updatable = false)
    private LocalDateTime createdTimestamp;

    @Column(name = "updated_timestamp", nullable = false)
    private LocalDateTime updatedTimestamp;

    @Column(name = "encrypted_file_hash", length = 64)
    private String encryptedFileHash;

    @Column(name = "thumbnail_file_path", length = 500)
    private String thumbnailFilePath;

    @Column(name = "thumbnail_status", nullable = false, length = 30)
    private String thumbnailStatus = "PENDING";

    @Column(name = "thumbnail_generated_timestamp")
    private LocalDateTime thumbnailGeneratedTimestamp;

    public Video() {
    }

    @PrePersist
    public void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdTimestamp = now;
        this.updatedTimestamp = now;

        if (this.status == null) {
            this.status = VideoStatus.UPLOADED;
        }

        if (this.isDeleted == null) {
            this.isDeleted = false;
        }
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedTimestamp = LocalDateTime.now();
    }

    // ---------------- Getters / Setters ----------------

    public Long getVideoId() {
        return videoId;
    }

    public void setVideoId(Long videoId) {
        this.videoId = videoId;
    }

    public String getVideoUid() {
        return videoUid;
    }

    public void setVideoUid(String videoUid) {
        this.videoUid = videoUid;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUploadUid() {
        return uploadUid;
    }

    public void setUploadUid(String uploadUid) {
        this.uploadUid = uploadUid;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getOriginalFilePath() {
        return originalFilePath;
    }

    public void setOriginalFilePath(String originalFilePath) {
        this.originalFilePath = originalFilePath;
    }

    public Long getOriginalFileSize() {
        return originalFileSize;
    }

    public void setOriginalFileSize(Long originalFileSize) {
        this.originalFileSize = originalFileSize;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public Long getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(Long durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public Integer getVideoWidth() {
        return videoWidth;
    }

    public void setVideoWidth(Integer videoWidth) {
        this.videoWidth = videoWidth;
    }

    public Integer getVideoHeight() {
        return videoHeight;
    }

    public void setVideoHeight(Integer videoHeight) {
        this.videoHeight = videoHeight;
    }

    public VideoStatus getStatus() {
        return status;
    }

    public void setStatus(VideoStatus status) {
        this.status = status;
    }

    public Boolean getDeleted() {
        return isDeleted;
    }

    public void setDeleted(Boolean deleted) {
        isDeleted = deleted;
    }

    public LocalDateTime getDeletedTimestamp() {
        return deletedTimestamp;
    }

    public void setDeletedTimestamp(LocalDateTime deletedTimestamp) {
        this.deletedTimestamp = deletedTimestamp;
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

    public String getEncryptedFileHash() {
        return encryptedFileHash;
    }

    public void setEncryptedFileHash(String encryptedFileHash) {
        this.encryptedFileHash = encryptedFileHash;
    }

    public String getThumbnailFilePath() {
        return thumbnailFilePath;
    }

    public void setThumbnailFilePath(String thumbnailFilePath) {
        this.thumbnailFilePath = thumbnailFilePath;
    }

    public String getThumbnailStatus() {
        return thumbnailStatus;
    }

    public void setThumbnailStatus(String thumbnailStatus) {
        this.thumbnailStatus = thumbnailStatus;
    }

    public LocalDateTime getThumbnailGeneratedTimestamp() {
        return thumbnailGeneratedTimestamp;
    }

    public void setThumbnailGeneratedTimestamp(LocalDateTime thumbnailGeneratedTimestamp) {
        this.thumbnailGeneratedTimestamp = thumbnailGeneratedTimestamp;
    }
}
