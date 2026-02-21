package com.company.image_service.service.impl;

import com.company.image_service.entity.Image;
import com.company.image_service.entity.UploadSession;
import com.company.image_service.repository.ImageRepository;
import com.company.image_service.repository.UploadSessionRepository;
import com.company.image_service.service.EncryptionService;
import com.company.image_service.service.MergeWorkerService;
import com.company.image_service.service.SyncManagerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.Optional;
import java.util.UUID;

@Service
public class MergeWorkerServiceImpl implements MergeWorkerService {

    private final UploadSessionRepository uploadRepository;
    private final ImageRepository imageRepository;
    private final EncryptionService encryptionService;
    private final SyncManagerService syncManagerService;

    @Value("${file.upload-dir:storage/users}")
    private String vaultDirPath;

    @Autowired
    public MergeWorkerServiceImpl(UploadSessionRepository uploadRepository,
            ImageRepository imageRepository,
            EncryptionService encryptionService,
            SyncManagerService syncManagerService) {
        this.uploadRepository = uploadRepository;
        this.imageRepository = imageRepository;
        this.encryptionService = encryptionService;
        this.syncManagerService = syncManagerService;
    }

    @Override
    @Async("mergeWorkerPool")
    public void processUploadSessionAsync(String uploadId) {
        UploadSession session = uploadRepository.findByUploadId(uploadId)
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
            Optional<Image> existingImage = imageRepository.findByUserIdAndContentHashAndIsDeletedFalse(
                    session.getUserId(), contentHash);

            if (existingImage.isPresent()) {
                // Deduplicate: Just count as synced, skip storage
                session.setStatus(UploadSession.UploadStatus.COMPLETED);
                uploadRepository.save(session);

                syncManagerService.incrementSyncUploadedCount(session.getSyncSessionId());
                return;
            }

            // 4. Encrypt to Vault Storage
            String storedFilename = UUID.randomUUID() + ".enc";
            Path userDir = Paths.get(vaultDirPath, String.valueOf(session.getUserId()));
            if (!Files.exists(userDir))
                Files.createDirectories(userDir);

            Path encryptedPath = userDir.resolve(storedFilename);

            try (InputStream is = new FileInputStream(mergedTempFile)) {
                encryptionService.encryptAndSave(is, encryptedPath);
            }

            // 5. Save Metadata
            Image image = new Image();
            image.setUserId(session.getUserId());
            image.setOriginalFilename(session.getOriginalFilename());
            image.setStoredFilename(storedFilename);
            image.setStoragePath(encryptedPath.toString());
            // Need a thumbnail strategy, maybe placeholder .enc for now
            image.setThumbnailPath(encryptedPath.toString() + ".thumb");
            image.setContentType("application/octet-stream"); // Will be decrypted on fly
            image.setFileSize(mergedTempFile.length());
            image.setContentHash(contentHash);

            imageRepository.save(image);

            // 6. Complete Session
            session.setStatus(UploadSession.UploadStatus.COMPLETED);
            uploadRepository.save(session);
            syncManagerService.incrementSyncUploadedCount(session.getSyncSessionId());

        } catch (Exception e) {
            session.setStatus(UploadSession.UploadStatus.FAILED);
            uploadRepository.save(session);
            // Log error here without sensitive details
        } finally {
            // 7. Always Clean Up Unencrypted Temp File
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
}
