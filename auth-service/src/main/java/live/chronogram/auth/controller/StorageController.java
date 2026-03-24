package live.chronogram.auth.controller;

import live.chronogram.auth.dto.StorageDetailsResponse;
import live.chronogram.auth.dto.StorageUsageResponse;
import live.chronogram.auth.service.StorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for monitoring user storage usage and cloud quotas.
 */
@RestController
@RequestMapping("/api/storage")
@CrossOrigin(origins = "*")
public class StorageController {

    @Autowired
    private StorageService storageService;

    /**
     * API Endpoint: GET /api/storage/usage
     * Returns aggregated metrics (total bytes, item counts) for the user's storage.
     * Optimized via caching in StorageService.
     */
    @GetMapping("/usage")
    public ResponseEntity<StorageUsageResponse> getStorageUsage(Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok(storageService.getStorageUsage(userId));
    }

    /**
     * API Endpoint: GET /api/storage/details
     * Returns a breakdown of storage by type (Images, Videos, etc.).
     */
    @GetMapping("/details")
    public ResponseEntity<StorageDetailsResponse> getStorageDetails(Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok(storageService.getStorageDetails(userId));
    }
}
