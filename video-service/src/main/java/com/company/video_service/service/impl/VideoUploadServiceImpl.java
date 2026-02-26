package com.company.video_service.service.impl; // Package for service implementations

import com.company.video_service.dto.VideoChunkUploadResponse; // DTO for chunk upload response
import com.company.video_service.dto.VideoFinalizeJobResponse; // DTO for finalize job response
import com.company.video_service.dto.VideoUploadInitRequest; // DTO for upload initialization request
import com.company.video_service.dto.VideoUploadInitResponse; // DTO for upload initialization response
import com.company.video_service.dto.VideoUploadStatusResponse; // DTO for upload status response
import com.company.video_service.entity.UploadSessionStatus; // Enum for upload session status
import com.company.video_service.entity.VideoJobType; // Enum for video job type
import com.company.video_service.entity.VideoProcessingJob; // Entity for video processing job
import com.company.video_service.entity.VideoProcessingJobStatus; // Enum for job status
import com.company.video_service.entity.VideoUploadChunk; // Entity for video upload chunk
import com.company.video_service.entity.VideoUploadSession; // Entity for upload session
import com.company.video_service.repository.VideoProcessingJobRepository; // Repository for processing jobs
import com.company.video_service.repository.VideoRepository; // Repository for video operations
import com.company.video_service.repository.VideoUploadChunkRepository; // Repository for chunks
import com.company.video_service.repository.VideoUploadSessionRepository; // Repository for sessions
import com.company.video_service.service.VideoUploadService; // Interface for upload service
import com.company.video_service.util.HashUtil; // Utility for hashing (SHA-256)
import org.springframework.stereotype.Service; // Spring Service annotation
import org.springframework.beans.factory.annotation.Value; // Spring Value annotation for properties

import java.time.LocalDateTime; // Java Time class
import java.util.UUID; // UUID class for generating unique IDs

@Service // Marks this class as a Spring Service component
public class VideoUploadServiceImpl implements VideoUploadService { // Implementation of VideoUploadService

    // Logger for logging events and errors
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(VideoUploadServiceImpl.class);

    private final VideoUploadSessionRepository sessionRepository; // Dependency: Session Repo
    private final VideoUploadChunkRepository chunkRepository; // Dependency: Chunk Repo
    private final VideoRepository videoRepository; // Dependency: Video Repo
    private final VideoProcessingJobRepository processingJobRepository; // Dependency: Job Repo

    @Value("${video.upload.checksum.enabled:true}") // Inject property to enable/disable checksums, default true
    private boolean checksumEnabled; // Flag for checksum validation

    @Value("${video.storage.temp-path}") // Inject temporary storage path from properties
    private String tempStoragePath; // Path where chunks are stored temporarily

    // Constructor injection for all dependencies
    public VideoUploadServiceImpl(VideoUploadSessionRepository sessionRepository,
            VideoUploadChunkRepository chunkRepository, VideoRepository videoRepository,
            VideoProcessingJobRepository processingJobRepository) {
        this.sessionRepository = sessionRepository; // Set session repo
        this.chunkRepository = chunkRepository; // Set chunk repo
        this.videoRepository = videoRepository; // Set video repo
        this.processingJobRepository = processingJobRepository; // Set job repo
    }

    @Override
    public VideoUploadInitResponse initUpload(Long userId, VideoUploadInitRequest request) { // Method to start upload

        // Validations: Check if file size is valid
        if (request.getFileSize() <= 0) {
            throw new RuntimeException("INVALID_FILE_SIZE"); // Error if size is 0 or negative
        }

        // Define max file size (e.g., 500MB)
        long maxSize = 500L * 1024 * 1024;
        if (request.getFileSize() > maxSize) {
            throw new RuntimeException("FILE_TOO_LARGE"); // Strict 500MB limit per BRD
        }

        // Daily upload quota (max 10 videos per day)
        java.time.LocalDateTime startOfDay = java.time.LocalDateTime.now().toLocalDate().atStartOfDay();
        long uploadsToday = videoRepository.countByUserIdAndCreatedTimestampAfter(userId, startOfDay);
        if (uploadsToday >= 10) {
            throw new RuntimeException("DAILY_UPLOAD_QUOTA_EXCEEDED");
        }

        // Define chunk size (fixed to 5MB for now)
        int chunkSize = 5 * 1024 * 1024;

        // Calculate total chunks required based on file size
        int totalChunks = (int) Math.ceil((double) request.getFileSize() / (double) chunkSize);

        // 1. Check if video already exists in final gallery for this user using
        // encryptedFileHash
        if (videoRepository.existsByUserIdAndEncryptedFileHashAndIsDeletedFalse(userId,
                request.getEncryptedFileHash())) {
            // If duplicate found, throw exception
            throw new RuntimeException("VIDEO_ALREADY_EXISTS");
        }

        // 2. Check if there is an active session (not expired, and not
        // failed/completed)
        var existingSession = sessionRepository
                .findFirstByUserIdAndOriginalFileNameAndOriginalFileSizeAndStatusInOrderByCreatedTimestampDesc(
                        userId, request.getFileName(), request.getFileSize(),
                        java.util.List.of(UploadSessionStatus.INITIATED, UploadSessionStatus.UPLOADING,
                                UploadSessionStatus.MERGING)); // Check for active statuses

        if (existingSession.isPresent()) { // If active session exists
            VideoUploadSession s = existingSession.get(); // Get session
            // Check if it's not expired
            if (LocalDateTime.now().isBefore(s.getExpiresTimestamp())) {
                // Return existing session details to allow resuming
                return new VideoUploadInitResponse(
                        s.getUploadUid(),
                        s.getChunkSizeBytes(),
                        s.getTotalChunks(),
                        s.getExpiresTimestamp(),
                        s.getStatus().name());
            }
        }

        // Generate a new unique ID for this upload session
        String uploadUid = UUID.randomUUID().toString();

        // Set expiration time (e.g., 24 hours from now)
        LocalDateTime expires = LocalDateTime.now().plusHours(24);

        // Create new session entity
        VideoUploadSession session = new VideoUploadSession();
        session.setUploadUid(uploadUid); // Set UID
        session.setUserId(userId); // Set User ID
        session.setOriginalFileName(request.getFileName()); // Set filename
        session.setOriginalFileSize(request.getFileSize()); // Set filesize
        session.setMimeType(request.getMimeType()); // Set MIME type
        session.setDurationSeconds(request.getDurationSeconds()); // Set duration
        session.setVideoWidth(request.getWidth()); // Set width
        session.setVideoHeight(request.getHeight()); // Set height
        session.setChunkSizeBytes(chunkSize); // Set chunk size
        session.setTotalChunks(totalChunks); // Set total chunks
        session.setUploadedChunksCount(0); // Initialize uploaded count to 0
        session.setStatus(UploadSessionStatus.INITIATED); // Set status to INITIATED
        session.setExpiresTimestamp(expires); // Set expiry
        session.setEncryptedFileHash(request.getEncryptedFileHash()); // Store client's encrypted hash

        sessionRepository.save(session); // Save session to DB

        // Return initialization response
        return new VideoUploadInitResponse(
                uploadUid,
                chunkSize,
                totalChunks,
                expires,
                session.getStatus().name());
    }

    @Override
    public VideoChunkUploadResponse uploadChunk(Long userId,
            String uploadUid,
            Integer chunkIndex,
            byte[] chunkBytes,
            String sha256) { // Method to handle chunk upload

        // Find session by UID, throw error if not found
        VideoUploadSession session = sessionRepository.findByUploadUid(uploadUid)
                .orElseThrow(() -> new RuntimeException("UPLOAD_SESSION_NOT_FOUND"));

        // Ownership check: Ensure user owns the session
        if (!session.getUserId().equals(userId)) {
            throw new RuntimeException("FORBIDDEN"); // Access denied
        }

        // Expiry check
        if (LocalDateTime.now().isAfter(session.getExpiresTimestamp())) {
            throw new RuntimeException("UPLOAD_SESSION_EXPIRED"); // Session expired
        }

        // Status check: Cannot upload if already completed
        if (session.getStatus() == UploadSessionStatus.COMPLETED) {
            throw new RuntimeException("UPLOAD_ALREADY_COMPLETED");
        }

        // Chunk index validation: Must be within valid range
        if (chunkIndex < 0 || chunkIndex >= session.getTotalChunks()) {
            throw new RuntimeException("INVALID_CHUNK_INDEX");
        }

        // Chunk size validation: Cannot be larger than defined chunk size
        if (chunkBytes.length > session.getChunkSizeBytes()) {
            throw new RuntimeException("CHUNK_TOO_LARGE");
        }

        // Checksum validation
        String computedHash = HashUtil.sha256Hex(chunkBytes); // Compute SHA256 of received bytes
        if (checksumEnabled) {
            if (sha256 == null || sha256.isBlank()) {
                throw new RuntimeException("MISSING_SHA256_HEADER"); // Header required if enabled
            }

            if ("SHA256_UNAVAILABLE".equals(sha256)) {
                log.warn("Checksum validation skipped for chunk {}: Frontend sent SHA256_UNAVAILABLE", chunkIndex);
            } else if (!computedHash.equalsIgnoreCase(sha256)) { // Compare computed vs received hash
                log.error("Checksum mismatch for chunk {}: expected={}, actual={}", chunkIndex, sha256, computedHash);
                throw new RuntimeException("CHUNK_CHECKSUM_MISMATCH"); // Error if mismatch
            }
        }

        // Idempotency check: Check if chunk already exists in DB
        var existingChunk = chunkRepository.findByUploadUidAndChunkIndex(uploadUid, chunkIndex);
        if (existingChunk.isPresent()) {
            // Return success if already exists (idempotent)
            return new VideoChunkUploadResponse(uploadUid, chunkIndex,
                    existingChunk.get().getChunkSizeBytes(),
                    "CHUNK_ALREADY_EXISTS");
        }

        // Define folder path for storing chunks
        String folderPath = tempStoragePath + "/" + uploadUid;
        java.io.File folder = new java.io.File(folderPath);

        // Create directory if it doesn't exist
        if (!folder.exists()) {
            boolean created = folder.mkdirs();
            if (!created && !folder.exists()) {
                log.error("Failed to create directory: {}", folderPath);
                throw new RuntimeException("FAILED_TO_CREATE_DIR: " + folderPath);
            }
        }

        // Define file path for the chunk
        String filePath = folderPath + "/" + chunkIndex + ".part";

        // Write chunk bytes to file
        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(filePath)) {
            fos.write(chunkBytes);
            log.debug("Chunk {} written to {} ({} bytes)", chunkIndex, filePath, chunkBytes.length);
        } catch (Exception e) {
            log.error("Failed to write chunk file: {}", filePath, e);
            throw new RuntimeException("CHUNK_WRITE_FAILED: " + e.getMessage());
        }

        // Save chunk metadata to DB
        VideoUploadChunk chunk = new VideoUploadChunk();
        chunk.setUploadUid(uploadUid);
        chunk.setChunkIndex(chunkIndex);
        chunk.setChunkFilePath(filePath);
        chunk.setChunkSizeBytes((long) chunkBytes.length);
        chunk.setSha256Checksum(computedHash);

        chunkRepository.save(chunk); // Save chunk entity

        // Update session status if this is the first chunk
        if (session.getStatus() == UploadSessionStatus.INITIATED) {
            session.setStatus(UploadSessionStatus.UPLOADING);
        }

        // Increment uploaded chunk count
        session.setUploadedChunksCount(session.getUploadedChunksCount() + 1);
        sessionRepository.save(session); // Update session

        // Return success response
        return new VideoChunkUploadResponse(uploadUid, chunkIndex,
                (long) chunkBytes.length,
                "CHUNK_UPLOADED");
    }

    @Override
    public VideoUploadStatusResponse getUploadStatus(Long userId, String uploadUid) { // Method to get status

        // Find session
        VideoUploadSession session = sessionRepository.findByUploadUid(uploadUid)
                .orElseThrow(() -> new RuntimeException("UPLOAD_SESSION_NOT_FOUND"));

        // Ownership check
        if (!session.getUserId().equals(userId)) {
            throw new RuntimeException("FORBIDDEN");
        }

        // Get all uploaded chunks for this session
        var chunks = chunkRepository.findByUploadUidOrderByChunkIndexAsc(uploadUid);

        // Collect indexes of uploaded chunks
        java.util.List<Integer> uploadedChunkIndexes = new java.util.ArrayList<>();
        for (var chunk : chunks) {
            uploadedChunkIndexes.add(chunk.getChunkIndex());
        }

        // Build status response
        VideoUploadStatusResponse response = new VideoUploadStatusResponse();
        response.setUploadUid(uploadUid);
        response.setFileName(session.getOriginalFileName());
        response.setFileSize(session.getOriginalFileSize());
        response.setChunkSizeBytes(session.getChunkSizeBytes());
        response.setTotalChunks(session.getTotalChunks());

        response.setUploadedChunks(uploadedChunkIndexes); // List of uploaded chunk indexes
        response.setUploadedCount(uploadedChunkIndexes.size()); // Count of uploaded chunks
        response.setRemainingChunks(session.getTotalChunks() - uploadedChunkIndexes.size()); // Remaining chunks

        response.setStatus(session.getStatus().name()); // Current status
        response.setExpiresTimestamp(session.getExpiresTimestamp()); // Expiry time

        return response;
    }

    @Override
    public VideoFinalizeJobResponse finalizeUpload(Long userId, String uploadUid) { // Method to finalize upload

        // Find session
        VideoUploadSession session = sessionRepository.findByUploadUid(uploadUid)
                .orElseThrow(() -> new RuntimeException("UPLOAD_SESSION_NOT_FOUND"));

        // Ownership check
        if (!session.getUserId().equals(userId)) {
            throw new RuntimeException("FORBIDDEN");
        }

        // Expiry check
        if (LocalDateTime.now().isAfter(session.getExpiresTimestamp())) {
            throw new RuntimeException("UPLOAD_SESSION_EXPIRED");
        }

        // Count actual uploaded chunks
        long uploadedCount = chunkRepository.countByUploadUid(uploadUid);

        // Verify all chunks are uploaded
        if (uploadedCount != session.getTotalChunks()) {
            throw new RuntimeException("ALL_CHUNKS_NOT_UPLOADED");
        }

        // Check if merging already started
        if (session.getStatus() == UploadSessionStatus.MERGING) {
            return new VideoFinalizeJobResponse(uploadUid, "MERGING_ALREADY_STARTED", session.getMergeJobUid());
        }

        // Check if already completed
        if (session.getStatus() == UploadSessionStatus.COMPLETED) {
            return new VideoFinalizeJobResponse(uploadUid, "UPLOAD_ALREADY_COMPLETED", session.getMergeJobUid());
        }

        // Update status to MERGING
        session.setStatus(UploadSessionStatus.MERGING);

        // Generate Merge Job UID
        String mergeJobUid = java.util.UUID.randomUUID().toString();
        session.setMergeJobUid(mergeJobUid);

        sessionRepository.save(session); // Save session update

        // Create a new background processing job for merging
        VideoProcessingJob job = new VideoProcessingJob(uploadUid);
        job.setJobUid(mergeJobUid);
        job.setUploadUid(uploadUid);
        job.setUserId(userId);
        job.setStatus(VideoProcessingJobStatus.PENDING); // Set job status to PENDING
        job.setJobType(VideoJobType.MERGE_UPLOAD); // Set job type

        processingJobRepository.save(job); // Save job

        // Return response indicating merging has started
        return new VideoFinalizeJobResponse(uploadUid, "MERGING_STARTED", mergeJobUid);
    }

    // Helper method to recursively delete a folder
    @SuppressWarnings("unused")
    private void deleteFolder(java.io.File file) {
        if (file == null || !file.exists()) {
            return;
        }

        if (file.isDirectory()) {
            java.io.File[] children = file.listFiles();
            if (children != null) {
                for (java.io.File child : children) {
                    deleteFolder(child); // Recursive delete
                }
            }
        }

        file.delete(); // Delete file or empty directory
    }

}
