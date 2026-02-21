package com.company.image_service.service.impl;

import com.company.image_service.entity.SyncSession;
import com.company.image_service.entity.UploadSession;
import com.company.image_service.repository.SyncSessionRepository;
import com.company.image_service.repository.UploadSessionRepository;
import com.company.image_service.service.SyncManagerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Optional;

@Service
public class SyncManagerServiceImpl implements SyncManagerService {

    private final SyncSessionRepository syncRepository;
    private final UploadSessionRepository uploadRepository;

    // Quotas (In a real app, these would come from application.yml or a config DB)
    private static final long MAX_DAILY_UPLOAD_BYTES = 5L * 1024 * 1024 * 1024; // 5GB
    private static final int MAX_CONCURRENT_UPLOADS = 3;

    @Autowired
    public SyncManagerServiceImpl(SyncSessionRepository syncRepository, UploadSessionRepository uploadRepository) {
        this.syncRepository = syncRepository;
        this.uploadRepository = uploadRepository;
    }

    @Override
    @Transactional
    public SyncSession startSyncSession(Long userId, SyncSession.TriggerType triggerType) {
        if (triggerType == SyncSession.TriggerType.AUTO_WIFI) {
            // Enforce 24-hour rule for AUTO syncs
            LocalDateTime twentyFourHoursAgo = LocalDateTime.now().minusHours(24);
            Optional<SyncSession> recentSession = syncRepository.findRecentSession(
                    userId, SyncSession.SyncStatus.COMPLETED, twentyFourHoursAgo);

            if (recentSession.isPresent()) {
                throw new IllegalStateException("An auto-sync has already completed in the last 24 hours.");
            }
        }

        SyncSession session = new SyncSession();
        session.setUserId(userId);
        session.setTriggerType(triggerType);
        session.setStatus(SyncSession.SyncStatus.INITIATED);
        session.setStartedAt(LocalDateTime.now());

        return syncRepository.save(session);
    }

    @Override
    @Transactional
    public void completeSyncSession(Long sessionId, int filesDetected, int filesSkipped) {
        SyncSession session = syncRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));

        session.setStatus(SyncSession.SyncStatus.COMPLETED);
        session.setCompletedAt(LocalDateTime.now());
        session.setTotalFilesDetected(filesDetected);
        session.setFilesSkipped(filesSkipped);

        syncRepository.save(session);
    }

    @Override
    public boolean canStartUpload(Long userId, Long fileSize) {
        // 1. Check concurrent limitations
        long activeUploads = uploadRepository.countByUserIdAndStatusIn(
                userId, Arrays.asList(UploadSession.UploadStatus.INITIATED, UploadSession.UploadStatus.UPLOADING,
                        UploadSession.UploadStatus.MERGING));

        if (activeUploads >= MAX_CONCURRENT_UPLOADS) {
            return false;
        }

        // 2. Check 24-hour size quota
        LocalDateTime twentyFourHoursAgo = LocalDateTime.now().minusHours(24);
        long dailyUploadedBytes = uploadRepository.sumFileSizeByUserIdAndStatusAndCreatedAtAfter(
                userId, Arrays.asList(UploadSession.UploadStatus.COMPLETED), twentyFourHoursAgo);

        if (dailyUploadedBytes + fileSize > MAX_DAILY_UPLOAD_BYTES) {
            return false;
        }

        return true;
    }

    @Override
    @Transactional
    public void incrementSyncUploadedCount(Long syncSessionId) {
        if (syncSessionId == null)
            return;

        syncRepository.findById(syncSessionId).ifPresent(session -> {
            session.setFilesUploaded(session.getFilesUploaded() + 1);
            syncRepository.save(session);
        });
    }
}
