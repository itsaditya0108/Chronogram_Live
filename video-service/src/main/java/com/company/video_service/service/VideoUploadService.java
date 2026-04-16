package com.company.video_service.service; // Package for service interfaces

import com.company.video_service.dto.*; // Import all DTOs

public interface VideoUploadService { // Interface defining video upload operations

    // Method to initialize a new video upload session
    VideoUploadInitResponse initUpload(Long userId, VideoUploadInitRequest request);

    // Method to upload a specific chunk of a video file
    VideoChunkUploadResponse uploadChunk(
            Long userId, // The user ID performing the upload
            String uploadUid, // Unique identifier for the upload session
            Integer chunkIndex, // Index of the chunk being uploaded
            byte[] chunkBytes, // The actual binary data of the chunk
            String sha256 // Optional SHA256 checksum for integrity verification
    );

    // Method to retrieve the current status of an upload session
    VideoUploadStatusResponse getUploadStatus(Long userId, String uploadUid);

    // Method to finalize the upload process after all chunks are received
    VideoFinalizeJobResponse finalizeUpload(Long userId, String uploadUid);

    java.util.List<com.company.video_service.dto.VideoBulkUploadResponseItem> bulkUpload(Long userId, org.springframework.web.multipart.MultipartFile[] files);
}
