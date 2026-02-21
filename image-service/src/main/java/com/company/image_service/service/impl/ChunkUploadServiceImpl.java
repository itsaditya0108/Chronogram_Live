package com.company.image_service.service.impl;

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

    private final UploadSessionRepository uploadRepository;

    @Value("${file.temp-dir:storage/temp}")
    private String tempDirPath;

    @Autowired
    public ChunkUploadServiceImpl(UploadSessionRepository uploadRepository) {
        this.uploadRepository = uploadRepository;
    }

    @Override
    @Transactional
    public UploadSession initiateUpload(Long userId, String originalFilename, int totalChunks, long totalFileSize,
            Long syncSessionId) {
        UploadSession session = new UploadSession();
        String uploadId = UUID.randomUUID().toString();

        session.setUploadId(uploadId);
        session.setUserId(userId);
        session.setOriginalFilename(originalFilename);
        session.setTotalChunks(totalChunks);
        session.setFileSize(totalFileSize);
        session.setStatus(UploadSession.UploadStatus.INITIATED);
        session.setSyncSessionId(syncSessionId);

        // Define temp file path for chunks
        String tempFilePath = Paths.get(tempDirPath, uploadId).toString();
        session.setTempFilePath(tempFilePath);

        // Ensure directory exists
        File dir = new File(tempDirPath);
        if (!dir.exists())
            dir.mkdirs();

        return uploadRepository.save(session);
    }

    @Override
    @Transactional
    public boolean receiveChunk(String uploadId, MultipartFile chunk, int chunkIndex) {
        UploadSession session = uploadRepository.findByUploadId(uploadId)
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
            Files.write(chunkPath, chunk.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            session.setReceivedChunks(session.getReceivedChunks() + 1);

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
        return uploadRepository.findByUploadId(uploadId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));
    }
}
