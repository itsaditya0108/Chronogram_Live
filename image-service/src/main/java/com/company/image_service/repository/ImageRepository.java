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

    Page<Image> findByUserIdAndIsDeletedFalse(Long userId, Pageable pageable);

    Optional<Image> findByIdAndUserIdAndIsDeletedFalse(Long id, Long userId);

    Optional<Image> findByUserIdAndOriginalFilenameAndIsDeletedFalse(Long userId, String originalFilename);

    Optional<Image> findFirstByUserIdAndContentHashAndIsDeletedFalseOrderByCreatedTimestampDesc(Long userId, String contentHash);

    List<Image> findByIsDeletedTrueAndDeletedTimestampBefore(LocalDateTime cutoff, Pageable pageable);

    Page<Image> findByUserIdAndIsDeletedFalseAndStoragePathStartingWith(Long userId, String pathPrefix, Pageable pageable);

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

    @Query(value = """
                SELECT 
                    (SELECT COUNT(*) FROM images WHERE user_id = :userId AND is_deleted = 0) as totalFiles,
                    (SELECT COALESCE(SUM(file_size), 0) FROM images WHERE user_id = :userId AND is_deleted = 0) as photoBytes
            """, nativeQuery = true)
    List<Object[]> getDetailedUserStorage(@Param("userId") Long userId);

    @Query(value = """
                SELECT COALESCE(SUM(file_size), 0) FROM images WHERE user_id = :userId AND is_deleted = 0
            """, nativeQuery = true)
    Long getTotalGlobalStorageByUser(@Param("userId") Long userId);
}
