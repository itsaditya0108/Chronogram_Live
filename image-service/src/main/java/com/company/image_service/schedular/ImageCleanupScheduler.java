package com.company.image_service.schedular;

import com.company.image_service.service.ImageCleanupService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class ImageCleanupScheduler {

    private final ImageCleanupService cleanupService;

    public ImageCleanupScheduler(ImageCleanupService cleanupService) {
        this.cleanupService = cleanupService;
    }

    // Runs every day at 3 AM
//    @Scheduled(cron = "0 0 3 * * ?")
//    public void runCleanup() {
//        cleanupService.cleanupDeletedImages();
//    }

    // @Scheduled(cron = "0 0 17 * * ?", zone = "Asia/Kolkata")
    public void runCleanup() {
        System.out.println("Cleanup job running at " + LocalDateTime.now());
        cleanupService.cleanupDeletedImages();
    }

}
