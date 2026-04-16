package live.chronogram.auth.controller;

import live.chronogram.auth.model.*;
import live.chronogram.auth.service.AdminService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    @Autowired
    private AdminService adminService;

    @GetMapping("/users")
    public ResponseEntity<List<User>> getAllUsers() {
        return ResponseEntity.ok(adminService.getAllUsers());
    }

    @GetMapping("/list")
    public ResponseEntity<List<Admin>> getAllAdmins() {
        return ResponseEntity.ok(adminService.getAllAdmins());
    }

    @GetMapping("/users/pending")
    public ResponseEntity<List<User>> getPendingUsers() {
        return ResponseEntity.ok(adminService.getPendingUsers());
    }

    @GetMapping("/users/approval")
    public ResponseEntity<List<User>> getApprovalList() {
        return ResponseEntity.ok(adminService.getApprovalList());
    }

    @PostMapping("/users/{userId}/approve")
    public ResponseEntity<Void> approveUser(@PathVariable Long userId, @RequestParam Long adminId) {
        adminService.approveUser(userId, adminId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/users/{userId}/reject")
    public ResponseEntity<Void> rejectUser(@PathVariable Long userId) {
        adminService.rejectUser(userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/users/{userId}/block")
    public ResponseEntity<Void> blockUser(@PathVariable Long userId) {
        adminService.blockUser(userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/users/{userId}/unblock")
    public ResponseEntity<Void> unblockUser(@PathVariable Long userId) {
        adminService.unblockUser(userId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/users/{userId}")
    public ResponseEntity<Void> softDeleteUser(@PathVariable Long userId) {
        adminService.softDeleteUser(userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/users/devices")
    public ResponseEntity<List<UserDevice>> getAllDevices() {
        return ResponseEntity.ok(adminService.getAllDevices());
    }

    @GetMapping("/users/storage")
    public ResponseEntity<List<StorageUsage>> getAllStorageUsage() {
        return ResponseEntity.ok(adminService.getAllStorageUsage());
    }

    @GetMapping("/users/storage/stats")
    public ResponseEntity<java.util.Map<String, Long>> getStorageStats() {
        List<StorageUsage> usages = adminService.getAllStorageUsage();
        long totalPhotos = usages.stream().mapToLong(StorageUsage::getPhotoBytes).sum();
        long totalVideos = usages.stream().mapToLong(StorageUsage::getVideoBytes).sum();
        long totalBytes = usages.stream().mapToLong(StorageUsage::getTotalBytes).sum();

        java.util.Map<String, Long> stats = new java.util.HashMap<>();
        stats.put("totalPhotos", totalPhotos);
        stats.put("totalVideos", totalVideos);
        stats.put("totalBytes", totalBytes);
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/users/sync")
    public ResponseEntity<List<SyncStatus>> getAllSyncStatus() {
        return ResponseEntity.ok(adminService.getAllSyncStatus());
    }

    @GetMapping("/users/incomplete")
    public ResponseEntity<List<IncompleteRegistration>> getAllIncompleteRegistrations() {
        return ResponseEntity.ok(adminService.getAllIncompleteRegistrations());
    }

    @PostMapping("/users/storage/sync")
    public ResponseEntity<Void> syncAllStorage() {
        adminService.syncAllStorage();
        return ResponseEntity.ok().build();
    }
}
