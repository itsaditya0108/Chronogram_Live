package com.example.authapp.controller.admin_controller;

import com.example.authapp.dto.admin.AdminFullDashboardResponse;
import com.example.authapp.services.admin.AdminFullDashboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/dashboard")
public class AdminFullDashboardController {

    private final AdminFullDashboardService adminFullDashboardService;

    public AdminFullDashboardController(AdminFullDashboardService adminFullDashboardService) {
        this.adminFullDashboardService = adminFullDashboardService;
    }

    @GetMapping("/full")
    public ResponseEntity<AdminFullDashboardResponse> getFullDashboard() {
        return ResponseEntity.ok(adminFullDashboardService.getFullDashboard());
    }
}
