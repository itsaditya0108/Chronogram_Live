package com.example.authapp.repository;

import com.example.authapp.entity.AdminAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, Long> {

    List<AdminAuditLog> findTop50ByOrderByCreatedTimestampDesc();

    List<AdminAuditLog> findTop50ByTargetUserIdOrderByCreatedTimestampDesc(Long userId);
}
