package com.company.image_service.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "sync_sessions", indexes = {
        @Index(name = "idx_sync_sessions_user", columnList = "user_id, started_at"),
        @Index(name = "idx_sync_sessions_status", columnList = "status")
})
public class SyncSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SyncStatus status;

    @Column(name = "total_files_detected")
    private Integer totalFilesDetected = 0;

    @Column(name = "files_uploaded")
    private Integer filesUploaded = 0;

    @Column(name = "files_skipped")
    private Integer filesSkipped = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 20)
    private TriggerType triggerType;

    public enum SyncStatus {
        INITIATED, RUNNING, COMPLETED, FAILED, EXPIRED
    }

    public enum TriggerType {
        MANUAL, AUTO_WIFI
    }

    @PrePersist
    protected void onCreate() {
        if (startedAt == null)
            startedAt = LocalDateTime.now();
    }

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public SyncStatus getStatus() {
        return status;
    }

    public void setStatus(SyncStatus status) {
        this.status = status;
    }

    public Integer getTotalFilesDetected() {
        return totalFilesDetected;
    }

    public void setTotalFilesDetected(Integer totalFilesDetected) {
        this.totalFilesDetected = totalFilesDetected;
    }

    public Integer getFilesUploaded() {
        return filesUploaded;
    }

    public void setFilesUploaded(Integer filesUploaded) {
        this.filesUploaded = filesUploaded;
    }

    public Integer getFilesSkipped() {
        return filesSkipped;
    }

    public void setFilesSkipped(Integer filesSkipped) {
        this.filesSkipped = filesSkipped;
    }

    public TriggerType getTriggerType() {
        return triggerType;
    }

    public void setTriggerType(TriggerType triggerType) {
        this.triggerType = triggerType;
    }
}
