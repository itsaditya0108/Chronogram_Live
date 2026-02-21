package com.company.image_service.service;

import com.company.image_service.dto.StorageSummaryResponse;
import com.company.image_service.dto.UserStorageResponse;
import com.company.image_service.repository.ImageRepository;
import org.springframework.stereotype.Service;

@Service
public class StorageService {

    private final ImageRepository imageRepository;

    public StorageService(ImageRepository imageRepository) {
        this.imageRepository = imageRepository;
    }

    public StorageSummaryResponse getSummary() {

        Object result = imageRepository.getStorageSummary();
        Object[] row = (Object[]) result;

        long totalFiles = ((Number) row[0]).longValue();
        long totalBytes = ((Number) row[1]).longValue();

        return new StorageSummaryResponse(totalFiles, totalBytes);
    }


    public UserStorageResponse getUserStorage(Long userId) {

        Object result = imageRepository.getUserStorage(userId);
        Object[] row = (Object[]) result;

        long totalFiles = ((Number) row[0]).longValue();
        long totalBytes = ((Number) row[1]).longValue();

        return new UserStorageResponse(userId, totalFiles, totalBytes);
    }

}
