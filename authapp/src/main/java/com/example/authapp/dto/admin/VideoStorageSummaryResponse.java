package com.example.authapp.dto.admin;

public class VideoStorageSummaryResponse {

    private long totalFiles;
    private long totalBytes;

    public long getTotalFiles() { return totalFiles; }
    public void setTotalFiles(long totalFiles) { this.totalFiles = totalFiles; }

    public long getTotalBytes() { return totalBytes; }
    public void setTotalBytes(long totalBytes) { this.totalBytes = totalBytes; }
}
