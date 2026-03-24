package live.chronogram.auth.controller;

import live.chronogram.auth.dto.SyncPreferenceRequest;
import live.chronogram.auth.service.SettingsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

import jakarta.validation.Valid;

/**
 * Controller for managing user-specific application preferences and sync settings.
 */
@RestController
@RequestMapping("/api/settings")
@CrossOrigin(origins = "*")
public class SettingsController {

    @Autowired
    private SettingsService settingsService;

    /**
     * API Endpoint: GET /api/settings/sync
     * Retrieves the storage sync preference (manual vs automatic) for the user.
     */
    @GetMapping("/sync")
    public ResponseEntity<?> getSyncPreference(Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        String mode = settingsService.getSyncPreference(userId);
        return ResponseEntity.ok(Map.of("mode", mode));
    }

    /**
     * API Endpoint: PUT /api/settings/sync
     * Updates the user's sync preference.
     */
    @PutMapping("/sync")
    public ResponseEntity<?> updateSyncPreference(@Valid @RequestBody SyncPreferenceRequest request,
            Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        settingsService.updateSyncPreference(userId, request);
        return ResponseEntity.ok(Map.of("message", "Sync preference updated"));
    }

    // --- Placeholder/Future Ready Endpoints ---

    /**
     * API Endpoint: GET /api/settings/notifications
     * Fetches toggle states for Push and Email notifications.
     */
    @GetMapping("/notifications")
    public ResponseEntity<?> getNotificationSettings(Authentication authentication) {
        // Logic: Placeholder for future implementation. Currently returns hardcoded defaults.
        return ResponseEntity.ok(Map.of("pushEnabled", true, "emailEnabled", false));
    }

    /**
     * API Endpoint: PUT /api/settings/notifications
     * Placeholder to update notification preferences.
     */
    @PutMapping("/notifications")
    public ResponseEntity<?> updateNotificationSettings(@RequestBody Map<String, Object> request,
            Authentication authentication) {
        // Logic: Placeholder
        return ResponseEntity.ok(Map.of("message", "Notification settings updated"));
    }
}
