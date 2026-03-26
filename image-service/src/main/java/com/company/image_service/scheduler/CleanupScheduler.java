package com.company.image_service.scheduler;

import com.company.image_service.entity.UploadSession;
import com.company.image_service.repository.UploadSessionRepository;
import com.company.image_service.service.ImageCleanupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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

    // Run every hour
    // @Scheduled(fixedRate = 3600000)
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
            log.info("Cleaned up {} expired upload sessions.", cleanedCount);
        }
    }
}
