package com.example.authapp.controller.admin_controller;

import com.example.authapp.entity.AdminAuditLog;
import com.example.authapp.repository.AdminAuditLogRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/audit-logs")
public class AdminAuditLogController {

    private final AdminAuditLogRepository repo;

    public AdminAuditLogController(AdminAuditLogRepository repo) {
        this.repo = repo;
    }

    @GetMapping
    public ResponseEntity<List<AdminAuditLog>> latestLogs() {
        return ResponseEntity.ok(repo.findTop50ByOrderByCreatedTimestampDesc());
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<AdminAuditLog>> userLogs(@PathVariable Long userId) {
        return ResponseEntity.ok(repo.findTop50ByTargetUserIdOrderByCreatedTimestampDesc(userId));
    }
}

