package com.company.image_service.service;

import com.company.image_service.entity.SyncSession;
import com.company.image_service.entity.UploadSession;

public interface SyncManagerService {

    /**
     * Attempts to start a new sync session. Validates the 24-hour rule if
     * applicable.
     */
    SyncSession startSyncSession(Long userId, SyncSession.TriggerType triggerType);

    /**
     * Marks a sync session as completed and records final metrics.
     */
    void completeSyncSession(Long sessionId, int filesDetected, int filesSkipped);

    /**
     * Validates if a user is allowed to start a new upload session based on quotas.
     * Checks max active sessions, max daily size, and max daily count.
     */
    boolean canStartUpload(Long userId, Long fileSize);

    /**
     * Increments the uploaded count for a specific sync session, if applicable.
     */
    void incrementSyncUploadedCount(Long syncSessionId);
}
