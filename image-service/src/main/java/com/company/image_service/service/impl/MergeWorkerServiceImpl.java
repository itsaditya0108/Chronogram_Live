package com.company.image_service.service.impl;

import com.company.image_service.entity.*;
import com.company.image_service.repository.*;
import java.util.Map;
import java.util.stream.Collectors;
import com.company.image_service.service.EncryptionService;
import com.company.image_service.service.MergeWorkerService;
import com.company.image_service.service.SyncManagerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Optional;
import java.util.Set;
import java.util.ArrayList;

@Service
public class MergeWorkerServiceImpl implements MergeWorkerService {

    private static final Logger logger = LoggerFactory.getLogger(MergeWorkerServiceImpl.class);

    private final UploadSessionRepository uploadRepository;
    private final ImageRepository imageRepository;
    private final EncryptionService encryptionService;
    private final SyncManagerService syncManagerService;
    private final ApprovedFormatRepository approvedFormatRepository;

    // Using VariantConfig for allowed extensions

    @Value("${image.storage.base-path:./data/image-service}")
    private String storageBasePath;

    @Autowired
    public MergeWorkerServiceImpl(UploadSessionRepository uploadRepository,
            ImageRepository imageRepository,
            EncryptionService encryptionService,
            SyncManagerService syncManagerService,
            ApprovedFormatRepository approvedFormatRepository) {
        this.uploadRepository = uploadRepository;
        this.imageRepository = imageRepository;
        this.encryptionService = encryptionService;
        this.syncManagerService = syncManagerService;
        this.approvedFormatRepository = approvedFormatRepository;
    }

    private java.util.List<String> getApprovedExtensions() {
        return approvedFormatRepository.findAllByIsActiveTrue().stream()
                .map(f -> f.getExtension().toLowerCase())
                .collect(java.util.stream.Collectors.toList());
    }

    @Override
    @Async("mergeWorkerPool")
    public void processUploadSessionAsync(String uploadId) {
        UploadSession session = uploadRepository.findFirstByUploadIdOrderByCreatedAtDesc(uploadId)
                .orElseThrow(() -> new IllegalArgumentException("Upload not found"));

        if (session.getStatus() != UploadSession.UploadStatus.MERGING) {
            return;
        }

        File mergedTempFile = null;
        try {
            // 1. Merge Chunks
            mergedTempFile = mergeChunks(session);

            // 2. Compute Content Hash
            String contentHash = computeHash(mergedTempFile);

            // 3. Deduplication Check
            java.util.Optional<Image> existingImage = imageRepository.findFirstByUserIdAndContentHashAndIsDeletedFalseOrderByCreatedTimestampDesc(
                    session.getUserId(), contentHash);

            if (existingImage.isPresent()) {
                // Deduplicate: Just count as synced, skip storage
                session.setStatus(UploadSession.UploadStatus.COMPLETED);
                uploadRepository.save(session);

                syncManagerService.incrementSyncUploadedCount(session.getSyncSessionId());
                return;
            }

            // 4. Decode image for thumbnailing and validation
            java.awt.image.BufferedImage source;
            try (InputStream is = new FileInputStream(mergedTempFile)) {
                source = javax.imageio.ImageIO.read(is);
            }

            if (source == null) {
                throw new RuntimeException("Merged file is not a valid image");
            }

            // 5. Encrypt and Save Original + Thumbnail using centralized utility
            com.company.image_service.dto.StoredImageResult result;
            try (InputStream is = new FileInputStream(mergedTempFile)) {
                result = com.company.image_service.util.FileStorageUtil.storeWithThumbnail(
                        is,
                        session.getOriginalFilename(),
                        session.getUserId(),
                        storageBasePath,
                        source,
                        "personal", // Default to personal for sync uploads
                        encryptionService);
            }

            // 6. Save Metadata
            Image image = new Image();
            image.setUserId(session.getUserId());
            image.setOriginalFilename(session.getOriginalFilename());
            image.setStoredFilename(result.getStoredFilename());
            image.setStoragePath(result.getOriginalPath());
            image.setThumbnailPath(result.getThumbnailPath());
            image.setWidth(result.getWidth());
            image.setHeight(result.getHeight());
            image.setContentType("image/jpeg"); // Standard for synced photos
            image.setFileSize(mergedTempFile.length());
            image.setContentHash(contentHash);

            // Scan for variants in the same storage location
            scanAndRegisterVariants(image, result.getOriginalPath());

            imageRepository.save(image);

            // 7. Complete Session
            session.setStatus(UploadSession.UploadStatus.COMPLETED);
            uploadRepository.save(session);
            syncManagerService.incrementSyncUploadedCount(session.getSyncSessionId());

        } catch (Exception e) {
            session.setStatus(UploadSession.UploadStatus.FAILED);
            uploadRepository.save(session);
            logger.error("Failed to process upload session {}: {}", uploadId, e.getMessage(), e);
        } finally {
            // 8. Always Clean Up Unencrypted Temp File

            if (mergedTempFile != null && mergedTempFile.exists()) {
                mergedTempFile.delete();
            }
            cleanupChunks(session);
        }
    }

    private File mergeChunks(UploadSession session) throws Exception {
        File mergedFile = new File(session.getTempFilePath() + "_merged");
        try (FileOutputStream fos = new FileOutputStream(mergedFile, true)) {
            for (int i = 0; i < session.getTotalChunks(); i++) {
                File chunkRaw = new File(session.getTempFilePath() + "_chunk_" + i);
                if (!chunkRaw.exists())
                    throw new Exception("Missing chunk: " + i);

                try (FileInputStream fis = new FileInputStream(chunkRaw)) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = fis.read(buffer)) != -1) {
                        fos.write(buffer, 0, read);
                    }
                }
            }
        }
        return mergedFile;
    }

    private String computeHash(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] byteArray = new byte[8192];
            int bytesCount;
            while ((bytesCount = fis.read(byteArray)) != -1) {
                digest.update(byteArray, 0, bytesCount);
            }
        }
        byte[] bytes = digest.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private void cleanupChunks(UploadSession session) {
        for (int i = 0; i < session.getTotalChunks(); i++) {
            File chunkRaw = new File(session.getTempFilePath() + "_chunk_" + i);
            if (chunkRaw.exists())
                chunkRaw.delete();
        }
    }

    private void scanAndRegisterVariants(Image image, String originalRelativePath) {
        try {
            java.nio.file.Path originalPath = java.nio.file.Paths.get(storageBasePath, originalRelativePath);
            java.nio.file.Path parentDir = originalPath.getParent();
            String baseName = originalPath.getFileName().toString();
            int dotIndex = baseName.lastIndexOf('.');
            if (dotIndex == -1)
                return;
            String nameWithoutExt = baseName.substring(0, dotIndex);

            java.util.List<String> getApprovedExtensions = getApprovedExtensions();
            java.nio.file.Files.list(parentDir).forEach(path -> {
                String fileName = path.getFileName().toString();
                if (fileName.startsWith(nameWithoutExt) && !fileName.equals(baseName)) {
                    String ext = "";
                    int lastDot = fileName.lastIndexOf('.');
                    if (lastDot != -1) {
                        ext = fileName.substring(lastDot).toLowerCase();
                    }

                            // Scanning variant registration is disabled as per user request (no per-file variants in DB)
                }
            });
        } catch (Exception e) {
            logger.warn("Error while scanning variants for image {}: {}", image.getId(), e.getMessage());
        }
    }
}
