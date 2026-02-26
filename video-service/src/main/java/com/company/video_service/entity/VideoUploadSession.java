package com.company.video_service.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "video_upload_sessions")
public class VideoUploadSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "video_upload_session_id")
    private Long videoUploadSessionId;

    @Column(name = "upload_uid", nullable = false, unique = true, length = 64)
    private String uploadUid;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "original_file_name", nullable = false, length = 255)
    private String originalFileName;

    @Column(name = "original_file_size", nullable = false)
    private Long originalFileSize;

    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "video_width")
    private Integer videoWidth;

    @Column(name = "video_height")
    private Integer videoHeight;

    @Column(name = "chunk_size_bytes", nullable = false)
    private Integer chunkSizeBytes;

    @Column(name = "total_chunks", nullable = false)
    private Integer totalChunks;

    @Column(name = "uploaded_chunks_count", nullable = false)
    private Integer uploadedChunksCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private UploadSessionStatus status = UploadSessionStatus.INITIATED;

    @Column(name = "merged_file_path", length = 500)
    private String mergedFilePath;

    @Column(name = "merged_file_size")
    private Long mergedFileSize;

    @Column(name = "merge_job_uid", length = 64)
    private String mergeJobUid;

    @Column(name = "error_code", length = 100)
    private String errorCode;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "expires_timestamp", nullable = false)
    private LocalDateTime expiresTimestamp;

    @Column(name = "created_timestamp", nullable = false, updatable = false)
    private LocalDateTime createdTimestamp;

    @Column(name = "updated_timestamp", nullable = false)
    private LocalDateTime updatedTimestamp;

    @Column(name = "merged_timestamp")
    private LocalDateTime mergedTimestamp;

    @Column(name = "encrypted_file_hash", length = 64)
    private String encryptedFileHash;

    public VideoUploadSession() {
    }

    @PrePersist
    public void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdTimestamp = now;
        this.updatedTimestamp = now;

        if (this.uploadedChunksCount == null) {
            this.uploadedChunksCount = 0;
        }

        if (this.status == null) {
            this.status = UploadSessionStatus.INITIATED;
        }
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedTimestamp = LocalDateTime.now();
    }

    // ---------------- Getters / Setters ----------------

    public Long getVideoUploadSessionId() {
        return videoUploadSessionId;
    }

    public void setVideoUploadSessionId(Long videoUploadSessionId) {
        this.videoUploadSessionId = videoUploadSessionId;
    }

    public String getUploadUid() {
        return uploadUid;
    }

    public void setUploadUid(String uploadUid) {
        this.uploadUid = uploadUid;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getOriginalFileName() {
        return originalFileName;
    }

    public void setOriginalFileName(String originalFileName) {
        this.originalFileName = originalFileName;
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

    public Integer getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(Integer durationSeconds) {
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

    public Integer getChunkSizeBytes() {
        return chunkSizeBytes;
    }

    public void setChunkSizeBytes(Integer chunkSizeBytes) {
        this.chunkSizeBytes = chunkSizeBytes;
    }

    public Integer getTotalChunks() {
        return totalChunks;
    }

    public void setTotalChunks(Integer totalChunks) {
        this.totalChunks = totalChunks;
    }

    public Integer getUploadedChunksCount() {
        return uploadedChunksCount;
    }

    public void setUploadedChunksCount(Integer uploadedChunksCount) {
        this.uploadedChunksCount = uploadedChunksCount;
    }

    public UploadSessionStatus getStatus() {
        return status;
    }

    public void setStatus(UploadSessionStatus status) {
        this.status = status;
    }

    public String getMergedFilePath() {
        return mergedFilePath;
    }

    public void setMergedFilePath(String mergedFilePath) {
        this.mergedFilePath = mergedFilePath;
    }

    public Long getMergedFileSize() {
        return mergedFileSize;
    }

    public void setMergedFileSize(Long mergedFileSize) {
        this.mergedFileSize = mergedFileSize;
    }

    public String getMergeJobUid() {
        return mergeJobUid;
    }

    public void setMergeJobUid(String mergeJobUid) {
        this.mergeJobUid = mergeJobUid;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
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

    public LocalDateTime getUpdatedTimestamp() {
        return updatedTimestamp;
    }

    public void setUpdatedTimestamp(LocalDateTime updatedTimestamp) {
        this.updatedTimestamp = updatedTimestamp;
    }

    public LocalDateTime getMergedTimestamp() {
        return mergedTimestamp;
    }

    public void setMergedTimestamp(LocalDateTime mergedTimestamp) {
        this.mergedTimestamp = mergedTimestamp;
    }

    public String getEncryptedFileHash() {
        return encryptedFileHash;
    }

    public void setEncryptedFileHash(String encryptedFileHash) {
        this.encryptedFileHash = encryptedFileHash;
    }
}
