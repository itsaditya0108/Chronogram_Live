package com.company.image_service.service.impl; // Package for service implementations

import com.company.image_service.entity.Image; // Image entity
import com.company.image_service.repository.ImageRepository; // Repository for image data
import com.company.image_service.service.ImageCleanupService; // Service interface
import org.springframework.beans.factory.annotation.Value; // Value annotation for property injection
import org.springframework.data.domain.PageRequest; // PageRequest for pagination
import org.springframework.stereotype.Service; // Service annotation
import org.springframework.transaction.annotation.Transactional; // Transaction management

import java.nio.file.Files; // File utility
import java.nio.file.Path; // Path interface
import java.nio.file.Paths; // Paths utility
import java.time.LocalDateTime; // Date/Time utility
import java.util.List; // List utility

/**
 * Service implementation for cleaning up deleted images.
 * This service handles the hard deletion of images that were soft-deleted
 * longer than the retention period.
 */
@Service
public class ImageCleanupServiceImpl implements ImageCleanupService {

    private final ImageRepository imageRepository; // Repository dependency
    private final String storageBasePath; // Base path for image storage
    private final int retentionDays; // Number of days to keep soft-deleted images
    private final int batchSize; // Number of images to process in one batch

    // Constructor injection with property values
    public ImageCleanupServiceImpl(
            ImageRepository imageRepository,
            @Value("${image.storage.base-path}") String storageBasePath,
            @Value("${image.cleanup.retention-days}") int retentionDays,
            @Value("${image.cleanup.batch-size}") int batchSize) {
        this.imageRepository = imageRepository;
        this.storageBasePath = storageBasePath;
        this.retentionDays = retentionDays;
        this.batchSize = batchSize;
    }

    /**
     * Finds and permanently removes soft-deleted images that have exceeded the
     * retention period.
     * This method is typically called by a scheduler.
     */
    @Override
    @Transactional
    public void cleanupDeletedImages() {

        // Calculate the cutoff date (now - retention days)
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);

        // Fetch a batch of images ready for cleanup
        List<Image> images = imageRepository.findByIsDeletedTrueAndDeletedTimestampBefore(
                cutoff,
                PageRequest.of(0, batchSize));

        // Iterate through each image
        for (Image image : images) {
            // Delete the physical files (original and thumbnail)
            deleteFileSafely(image.getStoragePath());
            deleteFileSafely(image.getThumbnailPath());
            // Hard delete the record from the database
            imageRepository.delete(image);
        }
    }

    /**
     * Helper method to delete a file from the filesystem without throwing
     * exceptions.
     * Logs errors instead of crashing the process.
     * 
     * @param relativePath The relative path of the file to delete.
     */
    private void deleteFileSafely(String relativePath) {
        if (relativePath == null)
            return; // Nothing to delete
        try {
            // Construct full path
            Path path = Paths.get(storageBasePath, relativePath);
            // Delete if exists
            Files.deleteIfExists(path);
        } catch (Exception ex) {
            // Log error but continue processing other files
            System.err.println("Failed to delete file: " + relativePath);
        }
    }
}
