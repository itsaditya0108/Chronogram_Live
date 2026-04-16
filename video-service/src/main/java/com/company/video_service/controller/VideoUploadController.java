package com.company.video_service.controller; // 📦 Package for video service controllers

import com.company.video_service.dto.VideoChunkUploadResponse; // DTO for chunk upload response
import com.company.video_service.dto.VideoFinalizeJobResponse; // DTO for finalize job response
import com.company.video_service.dto.VideoUploadInitRequest; // DTO for upload initialization request
import com.company.video_service.dto.VideoUploadInitResponse; // DTO for upload initialization response
import com.company.video_service.dto.VideoUploadResultResponse; // DTO for final upload result
import com.company.video_service.dto.VideoUploadStatusResponse; // DTO for upload status
import com.company.video_service.entity.UploadSessionStatus; // Enum for upload session status
import com.company.video_service.entity.Video; // Video entity class
import com.company.video_service.entity.VideoUploadSession; // Upload session entity
import com.company.video_service.repository.VideoRepository; // Repository for Video entity
import com.company.video_service.repository.VideoUploadSessionRepository; // Repository for Upload Session
import com.company.video_service.service.VideoUploadService; // Service for handling upload logic
import jakarta.validation.Valid; // Validation annotation
import org.springframework.http.ResponseEntity; // Spring's HTTP response entity
import org.springframework.web.bind.annotation.*; // Import all Spring Web annotations

@RestController // Marks this class as a REST controller to handle HTTP requests
@RequestMapping("/api/v1/videos/uploads") // Base URL path for all endpoints in this controller
public class VideoUploadController { // Controller class for managing video uploads

        private final VideoUploadService videoUploadService; // Service for upload business logic
        private final VideoUploadSessionRepository sessionRepository; // Repository for session data
        private final VideoRepository videoRepository; // Repository for video data

        // Constructor injection for dependencies
        public VideoUploadController(VideoUploadService videoUploadService,
                        VideoUploadSessionRepository sessionRepository, VideoRepository videoRepository) {
                this.videoUploadService = videoUploadService; // Initialize upload service
                this.sessionRepository = sessionRepository; // Initialize session repository
                this.videoRepository = videoRepository; // Initialize video repository
        }

        // Endpoint to initialize a new video upload session
        @PostMapping("/init")
        public ResponseEntity<VideoUploadInitResponse> initUpload(
                        @RequestAttribute("userId") Long userId, // Extract User ID from request attribute
                        @Valid @RequestBody VideoUploadInitRequest request) { // Validate and bind request body
                // Call service to initialize upload and return response
                return ResponseEntity.ok(videoUploadService.initUpload(userId, request));
        }

        // Endpoint to upload a specific chunk of the video
        @PutMapping("/{uploadUid}/chunks/{chunkIndex}")
        public ResponseEntity<VideoChunkUploadResponse> uploadChunk(
                        @RequestAttribute("userId") Long userId, // Extract User ID from request attribute
                        @PathVariable String uploadUid, // Extract upload session UID from URL
                        @PathVariable Integer chunkIndex, // Extract chunk index from URL
                        @RequestHeader(value = "X-Chunk-SHA256", required = false) String sha256, // Optional SHA256
                                                                                                  // checksum
                        @RequestBody byte[] chunkBytes) { // Bind request body to byte array (video data)
                // Call service to handle chunk upload
                return ResponseEntity.ok(
                                videoUploadService.uploadChunk(userId, uploadUid, chunkIndex, chunkBytes, sha256));
        }

        // Endpoint to check the status of an ongoing upload
        @GetMapping("/{uploadUid}/status")
        public ResponseEntity<VideoUploadStatusResponse> getUploadStatus(
                        @RequestAttribute("userId") Long userId, // Extract User ID from request attribute
                        @PathVariable String uploadUid) { // Extract upload UID
                // Call service to get upload status
                return ResponseEntity.ok(videoUploadService.getUploadStatus(userId, uploadUid));
        }

        // Endpoint to finalize the upload after all chunks are sent
        @PostMapping("/{uploadUid}/finalize")
        public ResponseEntity<VideoFinalizeJobResponse> finalizeUpload(
                        @RequestAttribute("userId") Long userId, // Extract User ID from request attribute
                        @PathVariable String uploadUid) { // Extract upload UID
                // Call service to finalize upload process
                return ResponseEntity.ok(videoUploadService.finalizeUpload(userId, uploadUid));
        }

        // Endpoint to get the final result of the upload
        @GetMapping("/{uploadUid}/result")
        public ResponseEntity<VideoUploadResultResponse> getUploadResult(
                        @RequestAttribute("userId") Long userId, // Extract User ID from request attribute
                        @PathVariable String uploadUid) { // Extract upload UID

                // Find the upload session by UID, throw error if not found
                VideoUploadSession session = sessionRepository.findByUploadUid(uploadUid)
                                .orElseThrow(() -> new RuntimeException("UPLOAD_SESSION_NOT_FOUND"));

                // Security check: Ensure the requesting user owns the session
                if (!session.getUserId().equals(userId)) {
                        throw new RuntimeException("FORBIDDEN"); // Access denied
                }

                // Check if the session failed
                if (session.getStatus() == UploadSessionStatus.FAILED) {
                        return ResponseEntity.ok(
                                        new VideoUploadResultResponse(uploadUid, "FAILED", null,
                                                        session.getErrorMessage())); // Return failure response
                }

                // Check if the session is not yet completed
                if (session.getStatus() != UploadSessionStatus.COMPLETED) {
                        return ResponseEntity.ok(
                                        new VideoUploadResultResponse(uploadUid, session.getStatus().name(), null,
                                                        null)); // Return current status
                }

                // Retrieve the final video entity
                Video video = videoRepository.findByUploadUid(uploadUid)
                                .orElseThrow(() -> new RuntimeException("VIDEO_NOT_FOUND"));

                // Return success response with video ID
                return ResponseEntity.ok(
                                new VideoUploadResultResponse(uploadUid, "COMPLETED", video.getVideoUid(), null));
        }

        // Endpoint for high-performance bulk upload (Sync)
        @PostMapping("/bulk")
        public ResponseEntity<java.util.List<com.company.video_service.dto.VideoBulkUploadResponseItem>> bulkUpload(
                        @RequestAttribute(value = "userId", required = true) Long userId,
                        @RequestParam("files") org.springframework.web.multipart.MultipartFile[] files) {
                // Call service to process multiple files in parallel
                return ResponseEntity.ok(videoUploadService.bulkUpload(userId, files));
        }

}
