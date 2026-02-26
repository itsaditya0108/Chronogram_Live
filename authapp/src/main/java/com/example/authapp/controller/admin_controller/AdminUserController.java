package com.example.authapp.controller.admin_controller;

import com.example.authapp.dto.admin.AdminUserDetailsResponse;
import com.example.authapp.dto.admin.AdminUserFullDetailsResponse;
import com.example.authapp.dto.admin.AdminUserListResponse;
import com.example.authapp.entity.Admin;
import com.example.authapp.services.admin.AdminAuditLogService;
import com.example.authapp.services.admin.AdminUserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;
    private final AdminAuditLogService auditLogService;

    public AdminUserController(AdminUserService adminUserService, AdminAuditLogService auditLogService) {
        this.adminUserService = adminUserService;
        this.auditLogService = auditLogService;
    }

    @GetMapping
    public ResponseEntity<Page<AdminUserListResponse>> getUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(adminUserService.getUsers(page, size));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<AdminUserDetailsResponse> getUserDetails(@PathVariable Long userId) {
        return ResponseEntity.ok(adminUserService.getUserDetails(userId));
    }

    @PostMapping("/{userId}/block")
    public ResponseEntity<String> blockUser(
            @PathVariable Long userId,
            @RequestParam(required = false) String reason,
            HttpServletRequest request) {

        String blockReason = reason != null ? reason : "Violation of terms";
        adminUserService.blockUser(userId, blockReason);

        Admin admin = (Admin) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        auditLogService.log(
                admin.getAdminId(),
                "BLOCK_USER",
                userId,
                "Blocked: " + blockReason,
                request.getRemoteAddr(),
                request.getHeader("User-Agent"));

        return ResponseEntity.ok("User blocked successfully");
    }

    @PostMapping("/{userId}/inactive")
    public ResponseEntity<String> inactiveUser(@PathVariable Long userId,
            HttpServletRequest request) {
        adminUserService.inactiveUser(userId);

        Admin admin = (Admin) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        auditLogService.log(
                admin.getAdminId(),
                "INACTIVE_USER",
                userId,
                "User marked inactive",
                request.getRemoteAddr(),
                request.getHeader("User-Agent"));
        return ResponseEntity.ok("User marked inactive successfully");
    }

    @PostMapping("/{userId}/unblock")
    public ResponseEntity<String> unblockUser(@PathVariable Long userId,
            HttpServletRequest request) {
        adminUserService.unblockUser(userId);
        Admin admin = (Admin) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        auditLogService.log(
                admin.getAdminId(),
                "UNBLOCK_USER",
                userId,
                "User unblocked",
                request.getRemoteAddr(),
                request.getHeader("User-Agent"));
        return ResponseEntity.ok("User unblocked successfully");
    }

    @GetMapping("/search")
    public ResponseEntity<Page<AdminUserListResponse>> searchUsers(
            @RequestParam String query,
            @RequestParam(required = false) String statusId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "userId") String sortBy,
            @RequestParam(defaultValue = "desc") String direction) {

        return ResponseEntity.ok(
                adminUserService.searchUsers(query, statusId, page, size, sortBy, direction));
    }

    @GetMapping("/{userId}/full-details")
    public ResponseEntity<AdminUserFullDetailsResponse> getFullDetails(@PathVariable Long userId) {
        return ResponseEntity.ok(adminUserService.getFullDetails(userId));
    }

}
