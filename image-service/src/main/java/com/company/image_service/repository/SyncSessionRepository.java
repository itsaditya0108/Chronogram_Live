package com.company.image_service.repository;

import com.company.image_service.entity.SyncSession;
import com.company.image_service.entity.SyncSession.SyncStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.List;

public interface SyncSessionRepository extends JpaRepository<SyncSession, Long> {

    @Query("SELECT s FROM SyncSession s WHERE s.userId = :userId AND s.status = :status AND s.startedAt >= :since ORDER BY s.startedAt DESC LIMIT 1")
    Optional<SyncSession> findRecentSession(
            @Param("userId") Long userId,
            @Param("status") SyncStatus status,
            @Param("since") LocalDateTime since);

    List<SyncSession> findByStatusAndStartedAtBefore(SyncStatus status, LocalDateTime cutoff);
}
