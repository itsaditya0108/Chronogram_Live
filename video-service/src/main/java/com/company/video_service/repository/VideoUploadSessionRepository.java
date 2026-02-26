package com.company.video_service.repository; // Package for repositories

import com.company.video_service.entity.VideoUploadSession; // Upload session entity
import org.springframework.data.jpa.repository.JpaRepository; // JPA Repository

import java.util.Optional; // Optional container

// Repository interface for accessing VideoUploadSession data (metadata about an ongoing or completed upload)
public interface VideoUploadSessionRepository extends JpaRepository<VideoUploadSession, Long> {

    // Retrieve a session by its unique upload UID
    Optional<VideoUploadSession> findByUploadUid(String uploadUid);

    // Check if a session exists with the given upload UID
    boolean existsByUploadUid(String uploadUid);

    // Find the most recent upload session for a user that matches specific file
    // details and status
    // Useful for resuming uploads or checking if a similar file is already being
    // processed
    java.util.Optional<VideoUploadSession> findFirstByUserIdAndOriginalFileNameAndOriginalFileSizeAndStatusInOrderByCreatedTimestampDesc(
            Long userId, String originalFileName, Long originalFileSize,
            java.util.List<com.company.video_service.entity.UploadSessionStatus> statuses);

    java.util.List<VideoUploadSession> findAllByStatusInAndExpiresTimestampBefore(
            java.util.List<com.company.video_service.entity.UploadSessionStatus> statuses,
            java.time.LocalDateTime timestamp);
}
