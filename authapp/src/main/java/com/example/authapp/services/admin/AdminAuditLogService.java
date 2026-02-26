package com.example.authapp.services.admin;

import com.example.authapp.entity.AdminAuditLog;
import com.example.authapp.repository.AdminAuditLogRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class AdminAuditLogService {

    private final AdminAuditLogRepository adminAuditLogRepository;

    public AdminAuditLogService(AdminAuditLogRepository adminAuditLogRepository) {
        this.adminAuditLogRepository = adminAuditLogRepository;
    }

    public void log(Long adminId, String action, Long targetUserId,
                    String description, String ipAddress, String userAgent) {

        AdminAuditLog log = new AdminAuditLog();
        log.setAdminId(adminId);
        log.setAction(action);
        log.setTargetUserId(targetUserId);
        log.setDescription(description);
        log.setIpAddress(ipAddress);
        log.setUserAgent(userAgent);
        log.setCreatedTimestamp(LocalDateTime.now());

        adminAuditLogRepository.save(log);
    }
}
