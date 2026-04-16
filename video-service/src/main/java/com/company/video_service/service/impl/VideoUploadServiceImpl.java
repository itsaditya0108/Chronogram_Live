package com.company.video_service.service.impl;

import com.company.video_service.dto.*;
import com.company.video_service.entity.*;
import com.company.video_service.repository.*;
import com.company.video_service.service.VideoUploadService;
import com.company.video_service.util.HashUtil;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.List;
import java.util.Arrays;
import java.util.stream.Collectors;

@Service
public class VideoUploadServiceImpl implements VideoUploadService {

    private static final Logger log = LoggerFactory.getLogger(VideoUploadServiceImpl.class);

    private final VideoUploadSessionRepository sessionRepository;
    private final VideoUploadChunkRepository chunkRepository;
    private final VideoRepository videoRepository;
    private final VideoProcessingJobRepository processingJobRepository;
    private final com.company.video_service.service.EncryptionService encryptionService;
    private final com.company.video_service.service.VideoThumbnailService thumbnailService;
    private final com.company.video_service.service.storage.FileStorageService fileStorageService;

    @Value("${video.upload.checksum.enabled:true}")
    private boolean checksumEnabled;

    @Value("${video.storage.temp-path}")
    private String tempStoragePath;

    public VideoUploadServiceImpl(VideoUploadSessionRepository sessionRepository,
            VideoUploadChunkRepository chunkRepository, VideoRepository videoRepository,
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
    public List<VideoBulkUploadResponseItem> bulkUpload(Long userId, MultipartFile[] files) {
        log.info("[BULK] Sync hit registered for User: {}", userId);

        if (files == null || files.length == 0) {
            log.warn("[BULK] No files selected for bulk upload.");
            throw new RuntimeException("NO_FILES_SELECTED");
        }

        log.info("[BULK] Starting parallel sync for {} files...", files.length);

        return Arrays.stream(files).parallel().map(file -> {
            String originalFileName = file.getOriginalFilename();
            log.info("[BULK] Processing file: {} ({} bytes)", originalFileName, file.getSize());

            try {
                // 1. Quota Check (10GB)
                Object summary = videoRepository.getStorageSummary();
                long totalBytesUsed = (long) ((Object[]) summary)[1];
                if (totalBytesUsed + file.getSize() > 10L * 1024 * 1024 * 1024) {
                    log.error("[BULK] Quota exceeded for file: {}", originalFileName);
                    return new VideoBulkUploadResponseItem(null, "ERROR: QUOTA_EXCEEDED", null, null, originalFileName);
                }

                // 2. Hash & Deduplication
                String hash = HashUtil.sha256Hex(file.getBytes());
                var existing = videoRepository.findByUserIdAndEncryptedFileHashAndIsDeletedFalse(userId, hash);
                if (!existing.isEmpty()) {
                    var v = existing.get(0);
                    log.info("[BULK] File already exists: {} -> {}", originalFileName, v.getVideoUid());
                    return new VideoBulkUploadResponseItem(v.getVideoUid(), "ALREADY_EXISTS", "/api/v1/videos/" + v.getVideoUid(), "/api/v1/videos/" + v.getVideoUid() + "/thumbnail", originalFileName);
                }

                // 3. Create Temp Directory
                Path tempDir = Paths.get(tempStoragePath, "bulk", UUID.randomUUID().toString());
                Files.createDirectories(tempDir);
                Path tempRawFile = tempDir.resolve("raw_" + originalFileName);
                Files.copy(file.getInputStream(), tempRawFile);

                // 4. Extract Metadata & Thumbnail (FROM RAW)
                log.info("[BULK] Generating metadata for: {}", originalFileName);
                Path tempThumbFile = tempDir.resolve("thumb_raw.jpg");
                var metadata = thumbnailService.generateAndSaveThumbnail(tempRawFile.toFile(), tempThumbFile.toFile());

                // 5. Encrypt Video Asset
                log.info("[BULK] Encrypting video: {}", originalFileName);
                Path encryptedVideoPath = tempDir.resolve("video.enc");
                encryptionService.encryptAndSave(Files.newInputStream(tempRawFile), encryptedVideoPath);

                // 6. Encrypt Thumbnail Asset (MUST BE ENCRYPTED ON S3)
                String thumbStoredPath = null;
                if (metadata != null && tempThumbFile.toFile().exists()) {
                    log.info("[BULK] Encrypting thumbnail: {}", originalFileName);
                    Path encryptedThumbPath = tempDir.resolve("thumb.enc");
                    encryptionService.encryptAndSave(Files.newInputStream(tempThumbFile), encryptedThumbPath);
                    thumbStoredPath = fileStorageService.store(encryptedThumbPath.toFile(), "thumb_" + originalFileName + ".enc", userId, "thumbnail");
                }

                // 7. Store Encrypted Video Asset
                log.info("[BULK] Uploading encrypted assets to S3: {}", originalFileName);
                String videoStoredPath = fileStorageService.store(encryptedVideoPath.toFile(), originalFileName + ".enc", userId, "video");

                // 8. DB Entry (Preserve Metadata)
                String videoUid = "v-" + UUID.randomUUID().toString().substring(0, 8);
                Video video = new Video();
                video.setVideoUid(videoUid);
                video.setUserId(userId);
                video.setUploadUid("BULK-" + UUID.randomUUID().toString().substring(0, 8));
                video.setTitle(originalFileName);
                video.setOriginalFilePath(videoStoredPath);
                video.setOriginalFileSize(file.getSize());
                video.setMimeType(file.getContentType());
                video.setEncryptedFileHash(hash);
                video.setStatus(VideoStatus.UPLOADED);

                if (metadata != null) {
                    video.setDurationSeconds(metadata.getDurationSeconds());
                    video.setVideoWidth(metadata.getWidth());
                    video.setVideoHeight(metadata.getHeight());
                    video.setThumbnailFilePath(thumbStoredPath);
                }

                videoRepository.save(video);
                log.info("[BULK] Sync Successful: {} -> {}", originalFileName, videoUid);

                // 9. CLEANUP VPS DISK (Zero local footprint)
                deleteFolder(tempDir.toFile());

                return new VideoBulkUploadResponseItem(videoUid, "UPLOADED", "/api/v1/videos/" + videoUid, "/api/v1/videos/" + videoUid + "/thumbnail", originalFileName);

            } catch (Exception e) {
                log.error("[BULK] Sync Failed for file: {}", originalFileName, e);
                return new VideoBulkUploadResponseItem(null, "FAILED: " + e.getMessage(), null, null, originalFileName);
            }
        }).collect(Collectors.toList());
    }

    @Override
    public VideoUploadInitResponse initUpload(Long userId, VideoUploadInitRequest request) {
        if (request.getFileSize() <= 0) throw new RuntimeException("INVALID_FILE_SIZE");
        long maxSize = 500L * 1024 * 1024;
        if (request.getFileSize() > maxSize) throw new RuntimeException("FILE_TOO_LARGE");

        int chunkSize = 5 * 1024 * 1024;
        int totalChunks = (int) Math.ceil((double) request.getFileSize() / chunkSize);

        if (videoRepository.existsByUserIdAndEncryptedFileHashAndIsDeletedFalse(userId, request.getEncryptedFileHash())) {
            throw new RuntimeException("VIDEO_ALREADY_EXISTS");
        }

        String uploadUid = UUID.randomUUID().toString();
        LocalDateTime expires = LocalDateTime.now().plusHours(24);

        VideoUploadSession session = new VideoUploadSession();
        session.setUploadUid(uploadUid);
        session.setUserId(userId);
        session.setOriginalFileName(request.getFileName());
        session.setOriginalFileSize(request.getFileSize());
        session.setMimeType(request.getMimeType());
        session.setChunkSizeBytes(chunkSize);
        session.setTotalChunks(totalChunks);
        session.setUploadedChunksCount(0);
        session.setStatus(UploadSessionStatus.INITIATED);
        session.setExpiresTimestamp(expires);
        session.setEncryptedFileHash(request.getEncryptedFileHash());

        sessionRepository.save(session);

        return new VideoUploadInitResponse(uploadUid, chunkSize, totalChunks, expires, session.getStatus().name());
    }

    @Override
    public VideoChunkUploadResponse uploadChunk(Long userId, String uploadUid, Integer chunkIndex, byte[] chunkBytes, String sha256) {
        VideoUploadSession session = sessionRepository.findByUploadUid(uploadUid).orElseThrow(() -> new RuntimeException("NOT_FOUND"));
        if (!session.getUserId().equals(userId)) throw new RuntimeException("FORBIDDEN");

        String folderPath = tempStoragePath + "/" + uploadUid;
        new File(folderPath).mkdirs();
        String filePath = folderPath + "/" + chunkIndex + ".part";

        try (FileOutputStream fos = new FileOutputStream(filePath)) {
            fos.write(chunkBytes);
        } catch (Exception e) {
            throw new RuntimeException("WRITE_FAILED");
        }

        VideoUploadChunk chunk = new VideoUploadChunk();
        chunk.setUploadUid(uploadUid);
        chunk.setChunkIndex(chunkIndex);
        chunk.setChunkFilePath(filePath);
        chunk.setChunkSizeBytes((long) chunkBytes.length);
        chunkRepository.save(chunk);

        if (session.getStatus() == UploadSessionStatus.INITIATED) session.setStatus(UploadSessionStatus.UPLOADING);
        session.setUploadedChunksCount(session.getUploadedChunksCount() + 1);
        sessionRepository.save(session);

        return new VideoChunkUploadResponse(uploadUid, chunkIndex, (long) chunkBytes.length, "CHUNK_UPLOADED");
    }

    @Override
    public VideoUploadStatusResponse getUploadStatus(Long userId, String uploadUid) {
        VideoUploadSession session = sessionRepository.findByUploadUid(uploadUid).orElseThrow(() -> new RuntimeException("NOT_FOUND"));
        if (!session.getUserId().equals(userId)) throw new RuntimeException("FORBIDDEN");

        List<VideoUploadChunk> chunks = chunkRepository.findByUploadUidOrderByChunkIndexAsc(uploadUid);
        List<Integer> indexes = chunks.stream().map(VideoUploadChunk::getChunkIndex).collect(Collectors.toList());

        VideoUploadStatusResponse response = new VideoUploadStatusResponse();
        response.setUploadUid(uploadUid);
        response.setTotalChunks(session.getTotalChunks());
        response.setUploadedChunks(indexes);
        response.setStatus(session.getStatus().name());
        return response;
    }

    @Override
    public VideoFinalizeJobResponse finalizeUpload(Long userId, String uploadUid) {
        VideoUploadSession session = sessionRepository.findByUploadUid(uploadUid).orElseThrow(() -> new RuntimeException("NOT_FOUND"));
        if (!session.getUserId().equals(userId)) throw new RuntimeException("FORBIDDEN");

        session.setStatus(UploadSessionStatus.MERGING);
        String mergeJobUid = UUID.randomUUID().toString();
        session.setMergeJobUid(mergeJobUid);
        sessionRepository.save(session);

        VideoProcessingJob job = new VideoProcessingJob(uploadUid);
        job.setJobUid(mergeJobUid);
        job.setUserId(userId);
        job.setStatus(VideoProcessingJobStatus.PENDING);
        job.setJobType(VideoJobType.MERGE_UPLOAD);
        processingJobRepository.save(job);

        return new VideoFinalizeJobResponse(uploadUid, "MERGING_STARTED", mergeJobUid);
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
