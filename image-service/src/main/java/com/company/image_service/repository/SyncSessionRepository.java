package com.company.image_service.repository;

import com.company.image_service.entity.SyncSession;
import com.company.image_service.entity.SyncSession.SyncStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.List;

public interface SyncSessionRepository extends JpaRepository<SyncSession, Long> {

    Optional<SyncSession> findFirstByUserIdAndStatusAndStartedAtAfterOrderByStartedAtDesc(
            Long userId,
            SyncStatus status,
            LocalDateTime since);

    List<SyncSession> findByStatusAndStartedAtBefore(SyncStatus status, LocalDateTime cutoff);
}
