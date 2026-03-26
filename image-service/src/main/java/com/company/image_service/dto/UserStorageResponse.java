package com.company.image_service.dto;

public class UserStorageResponse {

    private Long userId;
    private long totalFiles;
    private long totalBytes;
    private long photoBytes;
    private long videoBytes;

    public UserStorageResponse(Long userId, long totalFiles, long totalBytes, long photoBytes, long videoBytes) {
        this.userId = userId;
        this.totalFiles = totalFiles;
        this.totalBytes = totalBytes;
        this.photoBytes = photoBytes;
        this.videoBytes = videoBytes;
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

    public long getPhotoBytes() {
        return photoBytes;
    }

    public long getVideoBytes() {
        return videoBytes;
    }
}
