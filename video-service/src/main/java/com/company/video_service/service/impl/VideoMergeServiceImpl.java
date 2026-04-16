package com.company.video_service.service.impl;

import com.company.video_service.entity.UploadSessionStatus;
import com.company.video_service.entity.Video;
import com.company.video_service.entity.VideoProcessingJob;
import com.company.video_service.entity.VideoProcessingJobStatus;
import com.company.video_service.entity.VideoStatus;
import com.company.video_service.entity.VideoUploadSession;
import com.company.video_service.repository.*;
import com.company.video_service.service.VideoMergeService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.time.LocalDateTime;
import java.util.UUID;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class VideoMergeServiceImpl implements VideoMergeService {

    private static final Logger log = LoggerFactory.getLogger(VideoMergeServiceImpl.class);

    private final VideoUploadSessionRepository sessionRepository;
    private final VideoUploadChunkRepository chunkRepository;
    private final VideoRepository videoRepository;
    private final VideoProcessingJobRepository processingJobRepository;
    private final com.company.video_service.service.EncryptionService encryptionService;
    private final com.company.video_service.service.VideoThumbnailService thumbnailService;
    private final com.company.video_service.service.storage.FileStorageService fileStorageService;

    @Value("${video.storage.temp-path}")
    private String tempStoragePath;

    public VideoMergeServiceImpl(VideoUploadSessionRepository sessionRepository,
            VideoUploadChunkRepository chunkRepository,
            VideoRepository videoRepository,
            VideoProcessingJobRepository processingJobRepository,
            com.company.video_service.service.EncryptionService encryptionService,
            com.company.video_service.service.VideoThumbnailService thumbnailService,
            com.company.video_service.service.storage.FileStorageService fileStorageService) {
        this.sessionRepository = sessionRepository;
        this.chunkRepository = chunkRepository;
        this.videoRepository = videoRepository;
        this.processingJobRepository = processingJobRepository;
        this.encryptionService = encryptionService;
        this.thumbnailService = thumbnailService;
        this.fileStorageService = fileStorageService;
    }

    @Override
    public void processMergeJob(VideoProcessingJob job) {
        log.info("[JOB] Starting Merge Pipeline for: {}", job.getJobUid());
        job.setStatus(VideoProcessingJobStatus.RUNNING);
        job.setStartedTimestamp(LocalDateTime.now());
        processingJobRepository.save(job);

        try {
            mergeUpload(job.getUploadUid(), job);
            job.setStatus(VideoProcessingJobStatus.COMPLETED);
            job.setCompletedTimestamp(LocalDateTime.now());
            processingJobRepository.save(job);
            log.info("[JOB] Merge SUCCESS: {}", job.getJobUid());
        } catch (Exception e) {
            log.error("[JOB] Merge FAILED: {}", job.getJobUid(), e);
            job.setStatus(VideoProcessingJobStatus.FAILED);
            job.setErrorMessage(e.getMessage());
            job.setCompletedTimestamp(LocalDateTime.now());
            processingJobRepository.save(job);
        }
    }

    private void mergeUpload(String uploadUid, VideoProcessingJob job) {
        VideoUploadSession session = sessionRepository.findByUploadUid(uploadUid)
                .orElseThrow(() -> new RuntimeException("UPLOAD_SESSION_NOT_FOUND"));

        if (session.getStatus() != UploadSessionStatus.MERGING) {
            throw new RuntimeException("UPLOAD_SESSION_NOT_IN_MERGING_STATE");
        }

        File tempDir = new File(tempStoragePath, "merging_" + uploadUid);
        tempDir.mkdirs();
        File rawVideoFile = new File(tempDir, "raw_merged.mp4");
        File encryptedVideoFile = new File(tempDir, "video.enc");
        File rawThumbFile = new File(tempDir, "thumb_raw.jpg");
        File encryptedThumbFile = new File(tempDir, "thumb.enc");

        try {
            // 1. MERGE CHUNKS
            log.info("[MERGE] Merging {} chunks into final raw file...", session.getTotalChunks());
            try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(rawVideoFile))) {
                for (int i = 0; i < session.getTotalChunks(); i++) {
                    File chunkFile = new File(tempStoragePath + "/" + uploadUid + "/" + i + ".part");
                    if (!chunkFile.exists()) throw new RuntimeException("CHUNK_MISSING_" + i);
                    try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(chunkFile))) {
                        byte[] buffer = new byte[65536];
                        int read;
                        while ((read = bis.read(buffer)) != -1) bos.write(buffer, 0, read);
                    }
                }
            }

            // 2. METADATA (FROM RAW)
            log.info("[MERGE] Extracting Video Metadata & Generating Thumbnail...");
            var metadata = thumbnailService.generateAndSaveThumbnail(rawVideoFile, rawThumbFile);

            // 3. ENCRYPT VIDEO (AES-GCM)
            log.info("[MERGE] Encrypting Video Asset...");
            try (InputStream ris = new FileInputStream(rawVideoFile)) {
                encryptionService.encryptAndSave(ris, encryptedVideoFile.toPath());
            }

            // 4. ENCRYPT THUMBNAIL (AES-GCM)
            String thumbStoredPath = null;
            if (metadata != null && rawThumbFile.exists()) {
                log.info("[MERGE] Encrypting Thumbnail Asset...");
                try (InputStream tis = new FileInputStream(rawThumbFile)) {
                    encryptionService.encryptAndSave(tis, encryptedThumbFile.toPath());
                }
                thumbStoredPath = fileStorageService.store(encryptedThumbFile, "thumbnail.enc", session.getUserId(), "thumbnail");
            }

            // 5. STORAGE UPLOAD (S3)
            log.info("[MERGE] Uploading Encrypted Assets to Cloud Storage...");
            String videoStoredPath = fileStorageService.store(encryptedVideoFile, session.getOriginalFileName() + ".enc", session.getUserId(), "video");

            // 6. DB UPDATES & METADATA PERMANENCE
            String videoUid = UUID.randomUUID().toString();
            Video video = new Video();
            video.setVideoUid(videoUid);
            video.setUserId(session.getUserId());
            video.setUploadUid(uploadUid);
            video.setTitle(session.getOriginalFileName());
            video.setOriginalFilePath(videoStoredPath);
            video.setOriginalFileSize(session.getOriginalFileSize());
            video.setMimeType(session.getMimeType());
            video.setEncryptedFileHash(session.getEncryptedFileHash());

            if (metadata != null) {
                video.setDurationSeconds(metadata.getDurationSeconds());
                video.setVideoWidth(metadata.getWidth());
                video.setVideoHeight(metadata.getHeight());
                video.setThumbnailFilePath(thumbStoredPath);
            }

            video.setStatus(VideoStatus.UPLOADED);
            video.setCreatedTimestamp(LocalDateTime.now());
            video.setUpdatedTimestamp(LocalDateTime.now());
            videoRepository.save(video);

            session.setStatus(UploadSessionStatus.COMPLETED);
            session.setUpdatedTimestamp(LocalDateTime.now());
            sessionRepository.save(session);
            
            job.setVideoUid(videoUid);
            processingJobRepository.save(job);

            log.info("[MERGE] SUCCESS: Assets registered for videoUid: {}", videoUid);

            // 7. IMMEDIATE CLEANUP ON SUCCESS (Zero disk footprint after verified sync)
            log.info("[MERGE] Sync verified. Destroying local temporary files...");
            deleteFolder(tempDir);

        } catch (Exception e) {
            session.setStatus(UploadSessionStatus.FAILED);
            session.setErrorMessage(e.getMessage());
            sessionRepository.save(session);
            throw new RuntimeException("MERGE_PROCESS_FAILED: " + e.getMessage(), e);
        } finally {
            // ONLY CLEAN UP CHUNKS (those are useless after merge attempt)
            cleanupChunks(uploadUid);
            // DO NOT delete the tempDir here anymore if we want a safety net.
            // Actually, we'll delete it specifically in the 'try' block on success.
        }
    }

    private void cleanupChunks(String uploadUid) {
        File chunkDir = new File(tempStoragePath + "/" + uploadUid);
        deleteFolder(chunkDir);
        chunkRepository.deleteAllByUploadUid(uploadUid);
    }

    private void deleteFolder(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) deleteFolder(child);
            }
        }
        file.delete();
    }
}
