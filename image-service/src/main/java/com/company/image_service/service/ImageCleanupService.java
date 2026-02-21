package com.company.image_service.service; // Package for service interfaces

public interface ImageCleanupService {
    /**
     * Periodically cleans up deleted images or temporary files.
     * Implementations should handle the logic for finding and removing these
     * checks.
     */
    void cleanupDeletedImages();
}
