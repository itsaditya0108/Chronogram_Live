package com.company.image_service.dto;

import java.util.List;

public class ImageBulkUploadResponseDto {
    private int totalFiles;
    private int syncedCount; 
    private int skippedCount; 
    private int alreadyUploadedCount; 
    private List<ImageResponseDto> images;
    private List<String> errors;

    public ImageBulkUploadResponseDto() {}

    public ImageBulkUploadResponseDto(int totalFiles, int syncedCount, int skippedCount, int alreadyUploadedCount, List<ImageResponseDto> images, List<String> errors) {
        this.totalFiles = totalFiles;
        this.syncedCount = syncedCount;
        this.skippedCount = skippedCount;
        this.alreadyUploadedCount = alreadyUploadedCount;
        this.images = images;
        this.errors = errors;
    }

    public int getTotalFiles() { return totalFiles; }
    public void setTotalFiles(int totalFiles) { this.totalFiles = totalFiles; }
    public int getSyncedCount() { return syncedCount; }
    public void setSyncedCount(int syncedCount) { this.syncedCount = syncedCount; }
    public int getSkippedCount() { return skippedCount; }
    public void setSkippedCount(int skippedCount) { this.skippedCount = skippedCount; }
    public int getAlreadyUploadedCount() { return alreadyUploadedCount; }
    public void setAlreadyUploadedCount(int alreadyUploadedCount) { this.alreadyUploadedCount = alreadyUploadedCount; }
    public List<ImageResponseDto> getImages() { return images; }
    public void setImages(List<ImageResponseDto> images) { this.images = images; }
    public List<String> getErrors() { return errors; }
    public void setErrors(List<String> errors) { this.errors = errors; }
}
