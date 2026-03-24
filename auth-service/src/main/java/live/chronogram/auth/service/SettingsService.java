package live.chronogram.auth.service;

import live.chronogram.auth.dto.SyncPreferenceRequest;
import live.chronogram.auth.model.User;
import live.chronogram.auth.model.UserSettings;
import live.chronogram.auth.repository.UserRepository;
import live.chronogram.auth.repository.UserSettingsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing user-specific synchronization and app preferences.
 */
@Service
public class SettingsService {

    @Autowired
    private UserSettingsRepository userSettingsRepository;

    @Autowired
    private UserRepository userRepository;

    /**
     * Retrieves the current sync preference (e.g., WIFI_ONLY).
     * @param userId The unique user ID.
     * @return The sync mode string.
     */
    public String getSyncPreference(Long userId) {
        UserSettings settings = getOrCreateSettings(userId);
        return settings.getSyncMode();
    }

    /**
     * Updates the user's synchronization preference.
     * @param userId The unique user ID.
     * @param request Contains the new mode.
     */
    @Transactional
    public void updateSyncPreference(Long userId, SyncPreferenceRequest request) {
        String mode = request.getMode();
        if (!"WIFI_ONLY".equals(mode) && !"ANY_NETWORK".equals(mode)) {
            throw new RuntimeException("Invalid sync mode. Allowed: WIFI_ONLY, ANY_NETWORK");
        }

        UserSettings settings = getOrCreateSettings(userId);
        settings.setSyncMode(mode);
        userSettingsRepository.save(settings);
    }

    /**
     * Helper to retrieve or initialize settings for a user.
     * Enforces account-existence and deletion checks.
     */
    private UserSettings getOrCreateSettings(Long userId) {
        return userSettingsRepository.findById(userId).orElseGet(() -> {
            User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));

            if (Boolean.TRUE.equals(user.getIsDeleted())) {
                throw new live.chronogram.auth.exception.AuthException(org.springframework.http.HttpStatus.GONE,
                        "Account deleted.");
            }

            UserSettings newSettings = new UserSettings(userId, "WIFI_ONLY", "{}");
            return userSettingsRepository.save(newSettings);
        });
    }
}
