package live.chronogram.auth.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Controller for retrieving public application metadata.
 * Provides versioning, build information, and legal URLs.
 */
@RestController
@RequestMapping("/api/app")
@CrossOrigin(origins = "*")
public class AppInfoController {

    // Hardcoded for MVP, should be externalized in real app
    private static final String VERSION = "1.0.3";
    private static final int BUILD = 25;
    private static final String MIN_SUPPORTED = "1.0.0";
    private static final String PRIVACY_URL = "https://chronogram.live/privacy";

    /**
     * API Endpoint: GET /api/app/info
     * Returns the current versioning and compatibility metadata of the application.
     * Used by mobile apps to trigger 'Force Update' or 'Optional Update' prompts.
     */
    @GetMapping("/info")
    public ResponseEntity<?> getAppInfo() {
        return ResponseEntity.ok(Map.of(
                "version", VERSION,
                "build", BUILD,
                "minSupported", MIN_SUPPORTED));
    }

    /**
     * API Endpoint: GET /api/app/privacy
     * Returns the official Privacy Policy URL.
     */
    @GetMapping("/privacy")
    public ResponseEntity<?> getPrivacyPolicy() {
        return ResponseEntity.ok(Map.of("url", PRIVACY_URL));
    }
}
