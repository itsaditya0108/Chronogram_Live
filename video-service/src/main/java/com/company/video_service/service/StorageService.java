package com.company.video_service.service;

import com.company.video_service.dto.StorageSummaryResponse;
import com.company.video_service.dto.UserStorageResponse;
import com.company.video_service.repository.VideoRepository;
import org.springframework.stereotype.Service;

@Service
public class StorageService {

    private final VideoRepository videoRepository;

    public StorageService(VideoRepository videoRepository) {
        this.videoRepository = videoRepository;
    }

    public StorageSummaryResponse getSummary() {

        Object result = videoRepository.getStorageSummary();
        Object[] row = (Object[]) result;

        long totalFiles = ((Number) row[0]).longValue();
        long totalBytes = ((Number) row[1]).longValue();

        return new StorageSummaryResponse(totalFiles, totalBytes);
    }

    public UserStorageResponse getUserStorage(Long userId) {

        Object result = videoRepository.getUserStorage(userId);
        Object[] row = (Object[]) result;

        long totalFiles = ((Number) row[0]).longValue();
        long totalBytes = ((Number) row[1]).longValue();

        return new UserStorageResponse(userId, totalFiles, totalBytes);
    }
}

