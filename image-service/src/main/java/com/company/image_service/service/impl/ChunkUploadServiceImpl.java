package com.company.image_service.service.impl;

import com.company.image_service.dto.ImageUploadInitResponse;
import com.company.image_service.entity.Image;
import com.company.image_service.entity.UploadSession;
import com.company.image_service.repository.UploadSessionRepository;
import com.company.image_service.service.ChunkUploadService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

@Service
public class ChunkUploadServiceImpl implements ChunkUploadService {

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ChunkUploadServiceImpl.class);

    private final UploadSessionRepository uploadRepository;
    private final com.company.image_service.repository.ImageRepository imageRepository;

    @Value("${file.temp-dir:storage/temp}")
    private String tempDirPath;

    @Autowired
    public ChunkUploadServiceImpl(UploadSessionRepository uploadRepository,
                                 com.company.image_service.repository.ImageRepository imageRepository) {
        this.uploadRepository = uploadRepository;
        this.imageRepository = imageRepository;
    }

    @Override
    @Transactional
    public ImageUploadInitResponse initiateUpload(Long userId, String originalFilename, int totalChunks, long totalFileSize,
            Long syncSessionId, String contentHash) {
        
        // 1. Pre-upload deduplication check (Consistent with Video Service)
        if (contentHash != null) {
            java.util.Optional<Image> existing = imageRepository.findFirstByUserIdAndContentHashAndIsDeletedFalseOrderByCreatedTimestampDesc(userId, contentHash);
            if (existing.isPresent()) {
                Image img = existing.get();
                logger.info("Deduplication: Existing image found for user {} with hash {}. Returning existing record ID: {}",
                        userId, contentHash, img.getId());
                
                ImageUploadInitResponse response = new ImageUploadInitResponse();
                response.setStatus("ALREADY_EXISTS");
                response.setExistingImageId(img.getId());
                response.setImageUrl("/api/images/" + img.getId() + "/download");
                response.setThumbnailUrl("/api/images/" + img.getId() + "/thumbnail");
                return response;
            }
        }

        // 2. Check for active sessions to resume (Consistent with Video Service)
        java.util.Optional<UploadSession> existingSession = uploadRepository.findFirstByUserIdAndCheckSumAndStatusInOrderByCreatedAtDesc(
                userId, contentHash, java.util.List.of(UploadSession.UploadStatus.INITIATED, UploadSession.UploadStatus.UPLOADING, UploadSession.UploadStatus.MERGING)
        );

        if (existingSession.isPresent()) {
            UploadSession s = existingSession.get();
            if (java.time.LocalDateTime.now().isBefore(s.getExpiresAt())) {
                logger.info("Session Resume: Found active session {} with status {} for user {}", s.getUploadId(), s.getStatus(), userId);
                ImageUploadInitResponse response = new ImageUploadInitResponse(s.getUploadId(), s.getStatus().name());
                response.setExpiresAt(s.getExpiresAt());
                return response;
            }
        }

        UploadSession session = new UploadSession();
        String uploadId = UUID.randomUUID().toString();

        session.setUploadId(uploadId);
        session.setUserId(userId);
        session.setOriginalFilename(originalFilename);
        session.setTotalChunks(totalChunks);
        session.setFileSize(totalFileSize);
        session.setStatus(UploadSession.UploadStatus.INITIATED);
        session.setSyncSessionId(syncSessionId);
        session.setCheckSum(contentHash); // Use checksum field to store the requested hash for later verification

        // Define temp file path for chunks
        String tempFilePath = Paths.get(tempDirPath, uploadId).toString();
        session.setTempFilePath(tempFilePath);

        // Ensure directory exists
        File dir = new File(tempDirPath);
        if (!dir.exists())
            dir.mkdirs();

        uploadRepository.save(session);
        return new ImageUploadInitResponse(uploadId, session.getStatus().name());
    }

    @Override
    @Transactional
    public boolean receiveChunk(String uploadId, MultipartFile chunk, int chunkIndex) {
        UploadSession session = uploadRepository.findFirstByUploadIdOrderByCreatedAtDesc(uploadId)
                .orElseThrow(() -> new IllegalArgumentException("Upload session not found: " + uploadId));

        if (session.getStatus() != UploadSession.UploadStatus.INITIATED
                && session.getStatus() != UploadSession.UploadStatus.UPLOADING) {
            throw new IllegalStateException("Session is not in a receptive state: " + session.getStatus());
        }

        try {
            if (session.getStatus() == UploadSession.UploadStatus.INITIATED) {
                session.setStatus(UploadSession.UploadStatus.UPLOADING);
            }

            // For true parallel chunks this needs a RandomAccessFile, but standard chunk
            // uploads often come sequentially.
            // Using a designated file name for each chunk or appending. Appending requires
            // strict sequential uploads.
            // We'll write individual chunk files and merge later to support
            // parallel/out-of-order chunks gracefully.
            Path chunkPath = Paths.get(session.getTempFilePath() + "_chunk_" + chunkIndex);
            boolean isNewChunk = !Files.exists(chunkPath);
            
            Files.write(chunkPath, chunk.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            if (isNewChunk) {
                session.setReceivedChunks(session.getReceivedChunks() + 1);
            }

            boolean isComplete = session.getReceivedChunks().equals(session.getTotalChunks());
            if (isComplete) {
                session.setStatus(UploadSession.UploadStatus.MERGING);
            }

            uploadRepository.save(session);
            return isComplete;

        } catch (IOException e) {
            throw new RuntimeException("Failed to save chunk", e);
        }
    }

    @Override
    @Transactional
    public boolean receiveChunkBytes(String uploadId, byte[] chunkData, int chunkIndex) {
        UploadSession session = uploadRepository.findFirstByUploadIdOrderByCreatedAtDesc(uploadId)
                .orElseThrow(() -> new IllegalArgumentException("Upload session not found: " + uploadId));

        if (session.getStatus() != UploadSession.UploadStatus.INITIATED
                && session.getStatus() != UploadSession.UploadStatus.UPLOADING) {
            throw new IllegalStateException("Session is not in a receptive state: " + session.getStatus());
        }

        try {
            if (session.getStatus() == UploadSession.UploadStatus.INITIATED) {
                session.setStatus(UploadSession.UploadStatus.UPLOADING);
            }

            Path chunkPath = Paths.get(session.getTempFilePath() + "_chunk_" + chunkIndex);
            boolean isNewChunk = !Files.exists(chunkPath);

            Files.write(chunkPath, chunkData, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            if (isNewChunk) {
                session.setReceivedChunks(session.getReceivedChunks() + 1);
            }

            boolean isComplete = session.getReceivedChunks().equals(session.getTotalChunks());
            if (isComplete) {
                session.setStatus(UploadSession.UploadStatus.MERGING);
            }

            uploadRepository.save(session);
            return isComplete;

        } catch (IOException e) {
            throw new RuntimeException("Failed to save chunk", e);
        }
    }

    @Override
    public UploadSession getSessionStatus(String uploadId) {
        return uploadRepository.findFirstByUploadIdOrderByCreatedAtDesc(uploadId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));
    }
}
