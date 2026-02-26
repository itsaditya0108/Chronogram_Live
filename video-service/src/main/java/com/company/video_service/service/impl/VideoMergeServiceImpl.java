package com.company.video_service.service.impl; // Package for service implementations

import com.company.video_service.entity.UploadSessionStatus; // Enum for session status
import com.company.video_service.entity.Video; // Video entity
import com.company.video_service.entity.VideoProcessingJob; // Job entity
import com.company.video_service.entity.VideoProcessingJobStatus; // Job status enum
import com.company.video_service.entity.VideoStatus; // Video status enum
import com.company.video_service.entity.VideoUploadSession; // Session entity
import com.company.video_service.repository.*; // Import all repositories
import com.company.video_service.service.VideoMergeService; // Merge service interface
import org.springframework.beans.factory.annotation.Value; // Value annotation
import org.springframework.stereotype.Service; // Service annotation

import java.io.*; // Import IO classes
import java.time.LocalDateTime; // Import LocalDateTime
import java.util.UUID; // Import UUID

@Service // Marks this class as a Spring Service
public class VideoMergeServiceImpl implements VideoMergeService { // Implementation of VideoMergeService

    private final VideoUploadSessionRepository sessionRepository; // Repo for sessions
    private final VideoUploadChunkRepository chunkRepository; // Repo for chunks
    private final VideoRepository videoRepository; // Repo for videos
    private final VideoProcessingJobRepository processingJobRepository; // Repo for jobs

    @Value("${video.storage.temp-path}") // Inject temp path from properties
    private String tempStoragePath;

    @Value("${video.storage.final-path}") // Inject final path from properties
    private String finalStoragePath;

    // Constructor injection
    public VideoMergeServiceImpl(VideoUploadSessionRepository sessionRepository,
            VideoUploadChunkRepository chunkRepository,
            VideoRepository videoRepository,
            VideoProcessingJobRepository processingJobRepository) {
        this.sessionRepository = sessionRepository;
        this.chunkRepository = chunkRepository;
        this.videoRepository = videoRepository;
        this.processingJobRepository = processingJobRepository;
    }

    @Override
    public void processMergeJob(VideoProcessingJob job) { // Entry point for background merge job
        String uploadUid = job.getUploadUid(); // Get upload UID from job

        // update job status RUNNING
        job.setStatus(VideoProcessingJobStatus.RUNNING);
        job.setStartedTimestamp(LocalDateTime.now());
        processingJobRepository.save(job); // Checkpoint job as RUNNING

        try {
            // merge chunks + create video + thumbnail
            mergeUpload(uploadUid, job);

            // If successful, mark job as COMPLETED
            job.setStatus(VideoProcessingJobStatus.COMPLETED);
            job.setCompletedTimestamp(LocalDateTime.now());
            processingJobRepository.save(job);

        } catch (Exception e) {
            // If failed, mark job as FAILED and save error message
            job.setStatus(VideoProcessingJobStatus.FAILED);
            job.setErrorMessage(e.getMessage());
            job.setCompletedTimestamp(LocalDateTime.now());
            processingJobRepository.save(job);
        }
    }

    private void mergeUpload(String uploadUid, VideoProcessingJob job) { // Core logic for merging
        System.out.println("DEBUG: Starting mergeUpload for uploadUid=" + uploadUid);

        // Retrieve session, fail if not found
        VideoUploadSession session = sessionRepository.findByUploadUid(uploadUid)
                .orElseThrow(() -> new RuntimeException("UPLOAD_SESSION_NOT_FOUND"));

        // Ensure session is in MERGING state
        if (session.getStatus() != UploadSessionStatus.MERGING) {
            throw new RuntimeException("UPLOAD_SESSION_NOT_IN_MERGING_STATE");
        }

        try {
            // output folder structure: users/<userId>/videos/<year>/<month>/<uploadUid>/
            java.time.LocalDateTime now = java.time.LocalDateTime.now();
            String datePath = String.format("%d/%02d", now.getYear(), now.getMonthValue());
            String relativeBasePath = "users/" + session.getUserId() + "/videos/" + datePath + "/" + uploadUid;

            // Create output directory
            File outputDir = new File(finalStoragePath, relativeBasePath);

            if (!outputDir.exists()) {
                outputDir.mkdirs(); // Create dirs if missing
            }

            // Define final video file path
            File finalVideoFile = new File(outputDir, "original.mp4");
            System.out.println("DEBUG: Merging to " + finalVideoFile.getAbsolutePath());

            // merge chunks
            try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(finalVideoFile))) {

                // Iterate through all chunks in order
                for (int i = 0; i < session.getTotalChunks(); i++) {
                    File chunkFile = new File(tempStoragePath + "/" + uploadUid + "/" + i + ".part");
                    if (!chunkFile.exists()) {
                        throw new RuntimeException("CHUNK_FILE_MISSING_" + i); // Fail if any chunk is missing
                    }
                    // Read chunk and write to final file
                    try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(chunkFile))) {
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = bis.read(buffer)) != -1) {
                            bos.write(buffer, 0, bytesRead);
                        }
                    }
                }
            } catch (Exception e) {
                // If merge fails, update session status to FAILED
                System.out.println("DEBUG: Merge failed with exception: " + e.getMessage());
                session.setStatus(UploadSessionStatus.FAILED);
                session.setErrorCode("MERGE_FAILED");
                session.setErrorMessage(e.getMessage());
                session.setUpdatedTimestamp(LocalDateTime.now());
                sessionRepository.save(session);
                throw new RuntimeException("MERGE_FAILED: " + e.getMessage(), e);
            }

            // validate merged size against original expected size and strict 500MB limit
            long maxSize = 500L * 1024 * 1024;
            if (finalVideoFile.length() != session.getOriginalFileSize() || finalVideoFile.length() > maxSize) {
                System.out.println(
                        "DEBUG: Size mismatch or exceeds limit. Expected=" + session.getOriginalFileSize() + " Actual="
                                + finalVideoFile.length());
                session.setStatus(UploadSessionStatus.FAILED);
                session.setErrorCode("MERGED_FILE_SIZE_MISMATCH");
                session.setErrorMessage(
                        "Expected=" + session.getOriginalFileSize() + " actual=" + finalVideoFile.length());
                session.setUpdatedTimestamp(LocalDateTime.now());
                sessionRepository.save(session);
                throw new RuntimeException("MERGED_FILE_SIZE_MISMATCH_OR_EXCEEDS_LIMIT");
            }

            // validate encrypted file hash
            try {
                String computedHash = com.company.video_service.util.HashUtil
                        .sha256Hex(java.nio.file.Files.readAllBytes(finalVideoFile.toPath()));
                if (!computedHash.equalsIgnoreCase(session.getEncryptedFileHash())) {
                    System.out.println("DEBUG: Hash mismatch. Expected=" + session.getEncryptedFileHash() + " Actual="
                            + computedHash);
                    session.setStatus(UploadSessionStatus.FAILED);
                    session.setErrorCode("ENCRYPTED_FILE_HASH_MISMATCH");
                    session.setErrorMessage("Hash mismatch during verification.");
                    session.setUpdatedTimestamp(LocalDateTime.now());
                    sessionRepository.save(session);
                    throw new RuntimeException("ENCRYPTED_FILE_HASH_MISMATCH");
                }
            } catch (Exception e) {
                throw new RuntimeException("FAILED_TO_VERIFY_FILE_HASH", e);
            }

            // update upload session with final file path
            session.setMergedFilePath(finalVideoFile.getAbsolutePath());
            session.setMergedFileSize(finalVideoFile.length());
            session.setMergedTimestamp(LocalDateTime.now());
            session.setStatus(UploadSessionStatus.COMPLETED); // Mark session as COMPLETED
            session.setUpdatedTimestamp(LocalDateTime.now());
            sessionRepository.save(session);

            // create video record in database
            String videoUid = UUID.randomUUID().toString();
            Video video = new Video();
            video.setVideoUid(videoUid);
            video.setUserId(session.getUserId());
            video.setUploadUid(uploadUid);
            video.setTitle(session.getOriginalFileName());
            // Store relative path for portability
            video.setOriginalFilePath(toRelativePath(finalVideoFile.getAbsolutePath()));
            video.setOriginalFileSize(finalVideoFile.length());
            video.setMimeType(session.getMimeType());
            video.setDurationSeconds(session.getDurationSeconds());
            video.setVideoWidth(session.getVideoWidth());
            video.setVideoHeight(session.getVideoHeight());
            video.setStatus(VideoStatus.UPLOADED);
            video.setCreatedTimestamp(LocalDateTime.now());
            video.setUpdatedTimestamp(LocalDateTime.now());
            videoRepository.save(video); // Save video entity

            // update job with videoUid
            job.setVideoUid(videoUid);
            processingJobRepository.save(job);

        } finally {
            // cleanup chunk files + chunk rows to free space
            System.out.println("DEBUG: Cleaning up chunks for " + uploadUid);
            cleanupChunks(uploadUid);
        }
    }

    @org.springframework.transaction.annotation.Transactional
    private void cleanupChunks(String uploadUid) { // Helper to remove temp chunks
        try {
            File chunkDir = new File(tempStoragePath + "/" + uploadUid);

            if (chunkDir.exists()) {
                File[] files = chunkDir.listFiles();
                if (files != null) {
                    for (File f : files) {
                        f.delete(); // Delete individual chunk files
                    }
                }
                chunkDir.delete(); // Delete directory
                System.out.println("DEBUG: Deleted temp chunk directory: " + chunkDir.getAbsolutePath());
            }

            chunkRepository.deleteAllByUploadUid(uploadUid); // Remove chunk records from DB
            System.out.println("DEBUG: Deleted chunk records for uploadUid: " + uploadUid);

        } catch (Exception e) {
            // ignore cleanup failure (log only in production)
            System.out.println("Cleanup failed: " + e.getMessage());
        }
    }

    private String toRelativePath(String absolutePath) { // Helper to convert absolute path to relative
        if (absolutePath == null)
            return null;

        try {
            File baseFile = new File(finalStoragePath);
            File absFile = new File(absolutePath);

            String base = baseFile.getCanonicalPath();
            String abs = absFile.getCanonicalPath();

            // Case-insensitive check for Windows compatibility
            if (abs.toLowerCase().startsWith(base.toLowerCase())) {
                String relative = abs.substring(base.length());
                if (relative.startsWith(File.separator)) {
                    relative = relative.substring(1);
                }
                return relative.replace("\\", "/"); // Normalize slashes
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return absolutePath; // Return absolute if conversion fails
    }
}
