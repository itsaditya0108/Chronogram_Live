package com.company.image_service.repository;

import com.company.image_service.entity.UploadSession;
import com.company.image_service.entity.UploadSession.UploadStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.List;

public interface UploadSessionRepository extends JpaRepository<UploadSession, Long> {

    java.util.Optional<UploadSession> findFirstByUploadIdOrderByCreatedAtDesc(String uploadId);

    Optional<UploadSession> findFirstByUserIdAndCheckSumAndStatusInOrderByCreatedAtDesc(Long userId, String checkSum, List<UploadStatus> statuses);

    long countByUserIdAndStatusIn(Long userId, List<UploadStatus> statuses);

    @Query("SELECT COUNT(u) FROM UploadSession u WHERE u.userId = :userId AND u.status IN :statuses AND u.createdAt >= :since")
    long countActiveSessions(
            @Param("userId") Long userId,
            @Param("statuses") List<UploadStatus> statuses,
            @Param("since") LocalDateTime since);

    List<UploadSession> findByStatusAndExpiresAtBefore(UploadStatus status, LocalDateTime cutoff);

    @Query("SELECT COALESCE(SUM(u.fileSize), 0) FROM UploadSession u WHERE u.userId = :userId AND u.status IN :statuses AND u.createdAt >= :since")
    long sumFileSizeByUserIdAndStatusAndCreatedAtAfter(
            @Param("userId") Long userId,
            @Param("statuses") List<UploadStatus> statuses,
            @Param("since") LocalDateTime since);
}
