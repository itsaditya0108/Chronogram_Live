package com.company.image_service.service; // Package for service interfaces

import com.company.image_service.entity.UploadSession;

public interface ImageCleanupService {
    /**
     * Periodically cleans up deleted images or temporary files.
     * Implementations should handle the logic for finding and removing these
     * checks.
     */
    void cleanupDeletedImages();

    /**
     * Cleans up an individual expired upload session and its temporary files.
     * @param session The session to clean up.
     */
    void processIndividualSessionCleanup(UploadSession session);
}
