package com.company.video_service.repository; // Package for repositories

import com.company.video_service.entity.Video; // Video entity
import org.springframework.data.jpa.repository.JpaRepository; // JPA Repository
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional; // Optional container

// Repository interface for accessing Video entity data
public interface VideoRepository extends JpaRepository<Video, Long> {

  // Retrieve a video by its unique global ID
  Optional<Video> findByVideoUid(String videoUid);

  // Check if a video exists with the given UID
  boolean existsByVideoUid(String videoUid);

  // Find a video associated with a specific upload session ID
  java.util.Optional<Video> findByUploadUid(String uploadUid);

  // Check for duplicate videos based on user, title, and file size (ignoring
  // deleted ones)
  boolean existsByUserIdAndTitleAndOriginalFileSizeAndIsDeletedFalse(Long userId, String title,
      Long originalFileSize);

  boolean existsByUserIdAndEncryptedFileHashAndIsDeletedFalse(Long userId, String encryptedFileHash);

  long countByUserIdAndCreatedTimestampAfter(Long userId, java.time.LocalDateTime timestamp);

  // Retrieve all active (non-deleted) videos for a user, ordered by newest first
  java.util.List<Video> findAllByUserIdAndIsDeletedFalseOrderByCreatedTimestampDesc(Long userId);

  @Query("""
          SELECT COUNT(v), COALESCE(SUM(v.originalFileSize), 0)
          FROM Video v
          WHERE v.isDeleted = false
            AND v.status <> com.company.video_service.entity.VideoStatus.DELETED
      """)
  Object getStorageSummary();

  @Query("""
          SELECT COUNT(v), COALESCE(SUM(v.originalFileSize), 0)
          FROM Video v
          WHERE v.userId = :userId
            AND v.isDeleted = false
            AND v.status <> com.company.video_service.entity.VideoStatus.DELETED
      """)
  Object getUserStorage(@Param("userId") Long userId);

}
