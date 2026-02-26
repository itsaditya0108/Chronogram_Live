package com.company.video_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class VideoUploadInitRequest {

    @NotBlank
    private String fileName;

    @NotNull
    private Long fileSize;

    @NotBlank
    private String mimeType;

    private Integer durationSeconds;
    private Integer width;
    private Integer height;
    private Integer totalChunks;

    @NotBlank
    private String encryptedFileHash;

    public VideoUploadInitRequest() {
    }

    public Integer getTotalChunks() {
        return totalChunks;
    }

    public void setTotalChunks(Integer totalChunks) {
        this.totalChunks = totalChunks;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public Integer getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(Integer durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public Integer getWidth() {
        return width;
    }

    public void setWidth(Integer width) {
        this.width = width;
    }

    public Integer getHeight() {
        return height;
    }

    public void setHeight(Integer height) {
        this.height = height;
    }

    public String getEncryptedFileHash() {
        return encryptedFileHash;
    }

    public void setEncryptedFileHash(String encryptedFileHash) {
        this.encryptedFileHash = encryptedFileHash;
    }
}
