package com.company.video_service.dto;

public class VideoMetadata {
    private Long durationSeconds;
    private Integer width;
    private Integer height;
    private String thumbnailPath;

    public VideoMetadata(Long durationSeconds, Integer width, Integer height) {
        this.durationSeconds = durationSeconds;
        this.width = width;
        this.height = height;
    }

    public Long getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(Long durationSeconds) { this.durationSeconds = durationSeconds; }

    public Integer getWidth() { return width; }
    public void setWidth(Integer width) { this.width = width; }

    public Integer getHeight() { return height; }
    public void setHeight(Integer height) { this.height = height; }

    public String getThumbnailPath() { return thumbnailPath; }
    public void setThumbnailPath(String thumbnailPath) { this.thumbnailPath = thumbnailPath; }
}
