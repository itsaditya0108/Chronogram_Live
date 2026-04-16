package com.company.image_service.dto;

public class StoredImageResult {

    private final String storedFilename;
    private final String originalPath;
    private final String thumbnailPath;
    private final int width;
    private final int height;
    private final long totalEncryptedSize; // Field for 100% accurate storage reporting

    public StoredImageResult(
            String storedFilename,
            String originalPath,
            String thumbnailPath,
            int width,
            int height,
            long totalEncryptedSize
    ) {
        this.storedFilename = storedFilename;
        this.originalPath = originalPath;
        this.thumbnailPath = thumbnailPath;
        this.width = width;
        this.height = height;
        this.totalEncryptedSize = totalEncryptedSize;
    }

    public String getStoredFilename() { return storedFilename; }
    public String getOriginalPath() { return originalPath; }
    public String getThumbnailPath() { return thumbnailPath; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public long getTotalEncryptedSize() { return totalEncryptedSize; }
}
