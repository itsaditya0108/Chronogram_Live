package com.example.authapp.dto.admin;

import java.time.LocalDateTime;

public class AdminUserFullDetailsResponse {

    private Long userId;
    private String name;
    private String email;
    private String phone;
    private String status;

    private boolean emailVerified;
    private boolean phoneVerified;

    private LocalDateTime createdTimestamp;

    private long imageTotalFiles;
    private long imageTotalBytes;

    private long videoTotalFiles;
    private long videoTotalBytes;

    public AdminUserFullDetailsResponse(
            Long userId,
            String name,
            String email,
            String phone,
            String status,
            boolean emailVerified,
            boolean phoneVerified,
            LocalDateTime createdTimestamp,
            long imageTotalFiles,
            long imageTotalBytes,
            long videoTotalFiles,
            long videoTotalBytes
    ) {
        this.userId = userId;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.status = status;
        this.emailVerified = emailVerified;
        this.phoneVerified = phoneVerified;
        this.createdTimestamp = createdTimestamp;
        this.imageTotalFiles = imageTotalFiles;
        this.imageTotalBytes = imageTotalBytes;
        this.videoTotalFiles = videoTotalFiles;
        this.videoTotalBytes = videoTotalBytes;
    }


    public Long getUserId() { return userId; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getStatus() { return status; }

    public boolean isEmailVerified() { return emailVerified; }
    public boolean isPhoneVerified() { return phoneVerified; }

    public LocalDateTime getCreatedTimestamp() { return createdTimestamp; }

    public long getImageTotalFiles() { return imageTotalFiles; }
    public long getImageTotalBytes() { return imageTotalBytes; }


    public long getVideoTotalFiles() {
        return videoTotalFiles;
    }

    public long getVideoTotalBytes() {
        return videoTotalBytes;
    }
}
