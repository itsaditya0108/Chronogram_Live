package com.company.image_service.scheduler;

import com.company.image_service.entity.UploadSession;
import com.company.image_service.repository.UploadSessionRepository;
import com.company.image_service.service.ImageCleanupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class CleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(CleanupScheduler.class);
    
    private final UploadSessionRepository uploadRepository;
    private final ImageCleanupService cleanupService;

    @Autowired
    public CleanupScheduler(UploadSessionRepository uploadRepository, ImageCleanupService cleanupService) {
        this.uploadRepository = uploadRepository;
        this.cleanupService = cleanupService;
    }

    @Value("${image.storage.base-path:./data/image-service}")
    private String storagePath;

    /**
     * Cleans up expired database sessions.
     */
    @Scheduled(fixedRate = 3600000)
    public void cleanupExpiredUploadSessions() {
        LocalDateTime now = LocalDateTime.now();

        // Find INITIATED or UPLOADING sessions past their expiresAt
        List<UploadSession> expiredSessions = uploadRepository.findByStatusAndExpiresAtBefore(
                UploadSession.UploadStatus.INITIATED, now);

        expiredSessions.addAll(uploadRepository.findByStatusAndExpiresAtBefore(
                UploadSession.UploadStatus.UPLOADING, now));

        int cleanedCount = 0;
        for (UploadSession session : expiredSessions) {
            try {
                cleanupService.processIndividualSessionCleanup(session);
                cleanedCount++;
            } catch (Exception e) {
                log.error("Failed to cleanup expired session {}: {}", session.getUploadId(), e.getMessage());
            }
        }

        if (cleanedCount > 0) {
            log.info("[CLEANUP] Removed {} expired upload sessions from DB.", cleanedCount);
        }
    }

    /**
     * Perfect Zero Footprint - Storage Janitor
     * Runs every hour to sweep away any orphaned files in the temp directory.
     * Uses a 2-hour safety window.
     */
    @Scheduled(fixedRate = 3600000)
    public void storageJanitor() {
        java.nio.file.Path tempPath = java.nio.file.Paths.get(storagePath, "temp");
        java.io.File tempDir = tempPath.toFile();
        
        if (!tempDir.exists() || !tempDir.isDirectory()) {
            return;
        }

        log.info("[JANITOR] Scanning for stale temporary images in: {}", tempDir.getAbsolutePath());
        
        // 2-hour grace period for recovery as requested
        java.time.Instant threshold = java.time.Instant.now().minus(2, java.time.temporal.ChronoUnit.HOURS);
        
        int deletedCount = scanAndDelete(tempDir, threshold);
        
        if (deletedCount > 0) {
            log.info("[JANITOR] Removed {} stale temporary media files from disk.", deletedCount);
        }
    }

    private int scanAndDelete(java.io.File root, java.time.Instant threshold) {
        int count = 0;
        java.io.File[] children = root.listFiles();
        if (children == null) return 0;

        for (java.io.File child : children) {
            // Check if entry is older than the threshold
            if (java.time.Instant.ofEpochMilli(child.lastModified()).isBefore(threshold)) {
                if (deleteRecursively(child)) {
                    count++;
                }
            }
        }
        return count;
    }

    private boolean deleteRecursively(java.io.File file) {
        if (file.isDirectory()) {
            java.io.File[] children = file.listFiles();
            if (children != null) {
                for (java.io.File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        return file.delete();
    }
}
