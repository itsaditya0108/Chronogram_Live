package com.company.video_service.schedular;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.File;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Storage Cleanup Janitor
 * Ensures the VPS disk only stores metadata by purging stale temporary files.
 * Uses a 2-hour safety window for recovery.
 */
@Component
public class StorageCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(StorageCleanupScheduler.class);

    @Value("${video.storage.temp-path}")
    private String tempStoragePath;

    // Runs every hour (3600000 ms)
    @Scheduled(fixedRate = 3600000)
    public void cleanupStaleTempFiles() {
        log.info("[JANITOR] Starting hourly cleanup of stale temp files in: {}", tempStoragePath);
        
        File rootTempDir = new File(tempStoragePath);
        if (!rootTempDir.exists() || !rootTempDir.isDirectory()) {
            return;
        }

        // 2-hour grace period for recovery as requested
        Instant threshold = Instant.now().minus(2, ChronoUnit.HOURS);
        int deletedCount = scanAndDelete(rootTempDir, threshold);
        
        if (deletedCount > 0) {
            log.info("[JANITOR] Cleanup complete. Removed {} stale entries.", deletedCount);
        } else {
            log.info("[JANITOR] Cleanup complete. No stale files found.");
        }
    }

    private int scanAndDelete(File root, Instant threshold) {
        int count = 0;
        File[] children = root.listFiles();
        if (children == null) return 0;

        for (File child : children) {
            // Check if the file/folder is older than the threshold
            if (Instant.ofEpochMilli(child.lastModified()).isBefore(threshold)) {
                log.info("[JANITOR] Purging stale entry: {}", child.getAbsolutePath());
                if (deleteRecursively(child)) {
                    count++;
                }
            }
        }
        return count;
    }

    private boolean deleteRecursively(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        return file.delete();
    }
}
