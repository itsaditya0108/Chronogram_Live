package com.company.image_service.dto;

public class UploadInitRequest {
    private String originalFilename;
    private int totalChunks;
    private long totalFileSize;
    private Long syncSessionId;

    // Getters and Setters
    public String getOriginalFilename() {
        return originalFilename;
    }

    public void setOriginalFilename(String originalFilename) {
        this.originalFilename = originalFilename;
    }

    public int getTotalChunks() {
        return totalChunks;
    }

    public void setTotalChunks(int totalChunks) {
        this.totalChunks = totalChunks;
    }

    public long getTotalFileSize() {
        return totalFileSize;
    }

    public void setTotalFileSize(long totalFileSize) {
        this.totalFileSize = totalFileSize;
    }

    public Long getSyncSessionId() {
        return syncSessionId;
    }

    public void setSyncSessionId(Long syncSessionId) {
        this.syncSessionId = syncSessionId;
    }

    private String contentHash;

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }
}
