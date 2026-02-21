package com.company.image_service.dto;

public class UserStorageResponse {

    private Long userId;
    private long totalFiles;
    private long totalBytes;

    public UserStorageResponse(Long userId, long totalFiles, long totalBytes) {
        this.userId = userId;
        this.totalFiles = totalFiles;
        this.totalBytes = totalBytes;
    }

    public Long getUserId() {
        return userId;
    }

    public long getTotalFiles() {
        return totalFiles;
    }

    public long getTotalBytes() {
        return totalBytes;
    }
}
