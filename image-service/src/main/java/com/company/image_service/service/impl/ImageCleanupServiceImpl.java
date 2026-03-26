package com.company.image_service.service.impl; // Package for service implementations

import com.company.image_service.entity.Image;
import com.company.image_service.entity.UploadSession;
import com.company.image_service.repository.ImageRepository;
import com.company.image_service.repository.UploadSessionRepository;
import com.company.image_service.service.ImageCleanupService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Service implementation for cleaning up deleted images.
 * This service handles the hard deletion of images that were soft-deleted
 * longer than the retention period.
 */
@Service
public class ImageCleanupServiceImpl implements ImageCleanupService {

    private final ImageRepository imageRepository;
    private final UploadSessionRepository uploadRepository;
    private final String storageBasePath;
    private final int retentionDays;
    private final int batchSize;

    public ImageCleanupServiceImpl(
            ImageRepository imageRepository,
            UploadSessionRepository uploadRepository,
            @Value("${image.storage.base-path}") String storageBasePath,
            @Value("${image.cleanup.retention-days}") int retentionDays,
            @Value("${image.cleanup.batch-size}") int batchSize) {
        this.imageRepository = imageRepository;
        this.uploadRepository = uploadRepository;
        this.storageBasePath = storageBasePath;
        this.retentionDays = retentionDays;
        this.batchSize = batchSize;
    }

    /**
     * Finds and permanently removes soft-deleted images that have exceeded the
     * retention period.
     */
    @Override
    @Transactional
    public void cleanupDeletedImages() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        List<Image> images = imageRepository.findByIsDeletedTrueAndDeletedTimestampBefore(
                cutoff,
                PageRequest.of(0, batchSize));

        for (Image image : images) {
            deleteFileSafely(image.getStoragePath());
            deleteFileSafely(image.getThumbnailPath());
            imageRepository.delete(image);
        }
    }

    @Override
    @Transactional
    public void processIndividualSessionCleanup(UploadSession session) {
        session.setStatus(UploadSession.UploadStatus.EXPIRED);
        uploadRepository.save(session);

        // Delete temp chunks from disk
        if (session.getTempFilePath() != null) {
            cleanupChunks(session);
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

    private void deleteFileSafely(String relativePath) {
        if (relativePath == null)
            return;
        try {
            Path path = Paths.get(storageBasePath, relativePath);
            Files.deleteIfExists(path);
        } catch (Exception ex) {
            System.err.println("Failed to delete file: " + relativePath);
        }
    }
}
