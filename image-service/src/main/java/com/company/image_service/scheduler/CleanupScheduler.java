package com.company.image_service.scheduler;

import com.company.image_service.entity.UploadSession;
import com.company.image_service.repository.UploadSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.time.LocalDateTime;
import java.util.List;

@Component
public class CleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(CleanupScheduler.class);
    private final UploadSessionRepository uploadRepository;

    @Autowired
    public CleanupScheduler(UploadSessionRepository uploadRepository) {
        this.uploadRepository = uploadRepository;
    }

    // Run every hour
    @Scheduled(fixedRate = 3600000)
    @Transactional
    public void cleanupExpiredUploadSessions() {
        LocalDateTime now = LocalDateTime.now();

        // Find INITIATED or UPLOADING sessions past their expiresAt
        List<UploadSession> expiredSessions = uploadRepository.findByStatusAndExpiresAtBefore(
                UploadSession.UploadStatus.INITIATED, now);

        expiredSessions.addAll(uploadRepository.findByStatusAndExpiresAtBefore(
                UploadSession.UploadStatus.UPLOADING, now));

        for (UploadSession session : expiredSessions) {
            session.setStatus(UploadSession.UploadStatus.EXPIRED);
            uploadRepository.save(session);

            // Delete temp chunks from disk
            if (session.getTempFilePath() != null) {
                cleanupChunks(session);
            }
        }

        if (!expiredSessions.isEmpty()) {
            log.info("Cleaned up {} expired upload sessions.", expiredSessions.size());
        }
    }

    private void cleanupChunks(UploadSession session) {
        for (int i = 0; i < session.getTotalChunks(); i++) {
            File chunkRaw = new File(session.getTempFilePath() + "_chunk_" + i);
            if (chunkRaw.exists()) {
                chunkRaw.delete();
            }
        }
    }
}
