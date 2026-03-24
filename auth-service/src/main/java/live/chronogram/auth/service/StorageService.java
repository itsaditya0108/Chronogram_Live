package live.chronogram.auth.service;

import live.chronogram.auth.dto.StorageDetailsResponse;
import live.chronogram.auth.dto.StorageUsageResponse;
import live.chronogram.auth.model.StorageUsageCache;
import live.chronogram.auth.repository.StorageUsageCacheRepository;
import live.chronogram.auth.model.User;
import live.chronogram.auth.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Service for tracking user storage usage across different media types.
 * Aggregates data from image-service and video-service.
 */
@Service
public class StorageService {

    @Autowired
    private StorageUsageCacheRepository storageUsageCacheRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private org.springframework.web.client.RestTemplate restTemplate;

    // Hardcoded limit for now, ideally should from config or user subscription plan
    private static final Double STORAGE_LIMIT_GB = 10.0;
    private static final Double STORAGE_WARNING_THRESHOLD_GB = 9.0;

    // Service URLs (should be in properties in real scenario)
    private static final String IMAGE_SERVICE_URL = "http://localhost:8084";
    private static final String VIDEO_SERVICE_URL = "http://localhost:8085";

    /**
     * Aggregates storage info by conditionally checking cache or calling external
     * services.
     */
    /**
     * Calculates the total storage usage (GB) and checks against limits.
     * @param userId The unique user ID.
     * @return StorageUsageResponse with total usage and warning flags.
     */
    public StorageUsageResponse getStorageUsage(Long userId) {
        StorageUsageCache cache = updateAndGetCache(userId);

        Double totalUsed = cache.getPhotos() + cache.getVideos();
        totalUsed = round(totalUsed, 2);

        boolean warning = totalUsed >= STORAGE_WARNING_THRESHOLD_GB;

        return new StorageUsageResponse(totalUsed, STORAGE_LIMIT_GB, "GB", warning);
    }

    /**
     * Returns detailed breakdown of storage (images and videos).
     */
    /**
     * Provides a detailed breakdown of storage used by photos vs videos.
     * @param userId The unique user ID.
     * @return StorageDetailsResponse with breakdown in GB.
     */
    public StorageDetailsResponse getStorageDetails(Long userId) {
        StorageUsageCache cache = updateAndGetCache(userId);

        Double photos = round(cache.getPhotos(), 2);
        Double videos = round(cache.getVideos(), 2);

        return new StorageDetailsResponse(photos, videos, "GB");
    }

    /**
     * Syncs storage usage from external services and updates the local cache.
     * Enforces account-existence and deletion checks.
     */
    private StorageUsageCache updateAndGetCache(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));

        if (Boolean.TRUE.equals(user.getIsDeleted())) {
            throw new live.chronogram.auth.exception.AuthException(org.springframework.http.HttpStatus.GONE,
                    "Account deleted.");
        }

        StorageUsageCache cache = storageUsageCacheRepository.findById(userId)
                .orElse(new StorageUsageCache(userId, 0.0, 0.0));

        try {
            // Fetch from Image Service
            live.chronogram.auth.dto.ExtUserStorageResponse imageStorage = restTemplate.getForObject(
                    IMAGE_SERVICE_URL + "/internal/storage/user/" + userId,
                    live.chronogram.auth.dto.ExtUserStorageResponse.class);

            if (imageStorage != null) {
                // Convert bytes to GB
                cache.setPhotos((double) imageStorage.getTotalBytes() / (1024 * 1024 * 1024));
            }
        } catch (Exception e) {
            System.err.println("Failed to fetch image storage: " + e.getMessage());
        }

        try {
            // Fetch from Video Service
            live.chronogram.auth.dto.ExtUserStorageResponse videoStorage = restTemplate.getForObject(
                    VIDEO_SERVICE_URL + "/internal/storage/user/" + userId,
                    live.chronogram.auth.dto.ExtUserStorageResponse.class);

            if (videoStorage != null) {
                // Convert bytes to GB
                cache.setVideos((double) videoStorage.getTotalBytes() / (1024 * 1024 * 1024));
            }
        } catch (Exception e) {
            System.err.println("Failed to fetch video storage: " + e.getMessage());
        }

        return storageUsageCacheRepository.save(cache);
    }

    private Double round(Double value, int places) {
        if (places < 0)
            throw new IllegalArgumentException();
        if (value == null)
            return 0.0;

        BigDecimal bd = BigDecimal.valueOf(value);
        bd = bd.setScale(places, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }
}
