package com.company.image_service.dto;

import java.time.LocalDateTime;

public class ImageUploadInitResponse {

    private String uploadId;
    private String status;
    private Long existingImageId;
    private String imageUrl;
    private String thumbnailUrl;
    private LocalDateTime expiresAt;

    public ImageUploadInitResponse() {
    }

    public ImageUploadInitResponse(String uploadId, String status) {
        this.uploadId = uploadId;
        this.status = status;
    }

    public ImageUploadInitResponse(String uploadId, String status, Long existingImageId, String imageUrl, String thumbnailUrl) {
        this.uploadId = uploadId;
        this.status = status;
        this.existingImageId = existingImageId;
        this.imageUrl = imageUrl;
        this.thumbnailUrl = thumbnailUrl;
    }

    public String getUploadId() {
        return uploadId;
    }

    public void setUploadId(String uploadId) {
        this.uploadId = uploadId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getExistingImageId() {
        return existingImageId;
    }

    public void setExistingImageId(Long existingImageId) {
        this.existingImageId = existingImageId;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    public void setThumbnailUrl(String thumbnailUrl) {
        this.thumbnailUrl = thumbnailUrl;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }
}
