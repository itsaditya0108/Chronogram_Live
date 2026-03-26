package com.company.image_service.service;

import com.company.image_service.dto.StorageSummaryResponse;
import com.company.image_service.dto.UserStorageResponse;
import com.company.image_service.repository.ImageRepository;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class StorageService {

    private final ImageRepository imageRepository;

    public StorageService(ImageRepository imageRepository) {
        this.imageRepository = imageRepository;
    }

    public StorageSummaryResponse getSummary() {
        Object result = imageRepository.getStorageSummary();
        Object[] row = (Object[]) result;
        long totalFiles = (row[0] != null) ? ((Number) row[0]).longValue() : 0L;
        long totalBytes = (row[1] != null) ? ((Number) row[1]).longValue() : 0L;
        return new StorageSummaryResponse(totalFiles, totalBytes);
    }

    public UserStorageResponse getUserStorage(Long userId) {
        List<Object[]> results = imageRepository.getDetailedUserStorage(userId);
        if (results == null || results.isEmpty()) {
            return new UserStorageResponse(userId, 0L, 0L, 0L, 0L);
        }
        Object[] row = (Object[]) results.get(0);

        long totalFiles = (row[0] != null) ? ((Number) row[0]).longValue() : 0L;
        long photoBytes = (row[1] != null) ? ((Number) row[1]).longValue() : 0L;
        long videoBytes = 0L; // Separately fetched by auth-service now
        long totalBytes = photoBytes + videoBytes;

        return new UserStorageResponse(userId, totalFiles, totalBytes, photoBytes, videoBytes);
    }
}
