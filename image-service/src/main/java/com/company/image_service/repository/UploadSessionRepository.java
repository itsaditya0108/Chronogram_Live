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

    Optional<UploadSession> findByUploadId(String uploadId);

    long countByUserIdAndStatusIn(Long userId, List<UploadStatus> statuses);

    List<UploadSession> findByStatusAndExpiresAtBefore(UploadStatus status, LocalDateTime cutoff);

    @Query("SELECT COALESCE(SUM(u.fileSize), 0) FROM UploadSession u WHERE u.userId = :userId AND u.status IN :statuses AND u.createdAt >= :since")
    long sumFileSizeByUserIdAndStatusAndCreatedAtAfter(
            @Param("userId") Long userId,
            @Param("statuses") List<UploadStatus> statuses,
            @Param("since") LocalDateTime since);
}
