package com.company.video_service.dto;

public class VideoBulkUploadResponseItem {
    private String videoUid;
    private String status;
    private String videoUrl;
    private String thumbnailUrl;
    private String originalFilename;

    public VideoBulkUploadResponseItem(String videoUid, String status, String videoUrl, String thumbnailUrl, String originalFilename) {
        this.videoUid = videoUid;
        this.status = status;
        this.videoUrl = videoUrl;
        this.thumbnailUrl = thumbnailUrl;
        this.originalFilename = originalFilename;
    }

    // Getters and Setters
    public String getVideoUid() { return videoUid; }
    public void setVideoUid(String videoUid) { this.videoUid = videoUid; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getVideoUrl() { return videoUrl; }
    public void setVideoUrl(String videoUrl) { this.videoUrl = videoUrl; }

    public String getThumbnailUrl() { return thumbnailUrl; }
    public void setThumbnailUrl(String thumbnailUrl) { this.thumbnailUrl = thumbnailUrl; }

    public String getOriginalFilename() { return originalFilename; }
    public void setOriginalFilename(String originalFilename) { this.originalFilename = originalFilename; }
}
