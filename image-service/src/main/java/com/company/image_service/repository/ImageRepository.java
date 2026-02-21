package com.company.image_service.repository;

import com.company.image_service.entity.Image;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ImageRepository extends JpaRepository<Image, Long> {

    /**
     * Fetch images owned by a user (not deleted)
     */
    Page<Image> findByUserIdAndIsDeletedFalse(
            Long userId,
            Pageable pageable);

    /**
     * Fetch a single image by id, ensuring ownership
     */
    Optional<Image> findByIdAndUserIdAndIsDeletedFalse(
            Long id,
            Long userId);

    /**
     * Check for duplicate filename for a user
     */
    Optional<Image> findByUserIdAndOriginalFilenameAndIsDeletedFalse(
            Long userId,
            String originalFilename);

    /**
     * Check for duplicate content hash for a user (Deduplication)
     */
    Optional<Image> findByUserIdAndContentHashAndIsDeletedFalse(
            Long userId,
            String contentHash);

    /**
     * Delete image by id, ensuring ownership
     */
    /**
     * Delete image by id, ensuring ownership
     */
    List<Image> findByIsDeletedTrueAndDeletedTimestampBefore(
            LocalDateTime cutoff,
            Pageable pageable);

    /**
     * Filter by storage path prefix (e.g. "users/" vs "shared_images/")
     */
    Page<Image> findByUserIdAndIsDeletedFalseAndStoragePathStartingWith(
            Long userId,
            String pathPrefix,
            Pageable pageable);

    @Query("""
                SELECT COUNT(i), COALESCE(SUM(i.fileSize), 0)
                FROM Image i
                WHERE i.isDeleted = false
            """)
    Object getStorageSummary();

    @Query("""
                SELECT COUNT(i), COALESCE(SUM(i.fileSize), 0)
                FROM Image i
                WHERE i.userId = :userId
                  AND i.isDeleted = false
            """)
    Object getUserStorage(@Param("userId") Long userId);

}
