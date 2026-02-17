package com.company.video_service.dto;

public class StorageSummaryResponse {

    private long totalFiles;
    private long totalBytes;

    public StorageSummaryResponse(long totalFiles, long totalBytes) {
        this.totalFiles = totalFiles;
        this.totalBytes = totalBytes;
    }

    public long getTotalFiles() {
        return totalFiles;
    }

    public long getTotalBytes() {
        return totalBytes;
    }
}
