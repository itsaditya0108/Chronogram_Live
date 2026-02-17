package com.example.authapp.dto.admin;

public class AdminFullDashboardResponse {

    private long totalUsers;
    private long activeUsers;
    private long inactiveUsers;
    private long blockedUsers;
    private long deletedUsers;

    private long imageTotalFiles;
    private long imageTotalBytes;

    private long videoTotalFiles;
    private long videoTotalBytes;

    private long totalStorageBytes;

    public AdminFullDashboardResponse(
            long totalUsers,
            long activeUsers,
            long inactiveUsers,
            long blockedUsers,
            long deletedUsers,
            long imageTotalFiles,
            long imageTotalBytes,
            long videoTotalFiles,
            long videoTotalBytes
    ) {
        this.totalUsers = totalUsers;
        this.activeUsers = activeUsers;
        this.inactiveUsers = inactiveUsers;
        this.blockedUsers = blockedUsers;
        this.deletedUsers = deletedUsers;

        this.imageTotalFiles = imageTotalFiles;
        this.imageTotalBytes = imageTotalBytes;

        this.videoTotalFiles = videoTotalFiles;
        this.videoTotalBytes = videoTotalBytes;

        this.totalStorageBytes = imageTotalBytes + videoTotalBytes;
    }

    public long getTotalUsers() { return totalUsers; }
    public long getActiveUsers() { return activeUsers; }
    public long getInactiveUsers() { return inactiveUsers; }
    public long getBlockedUsers() { return blockedUsers; }
    public long getDeletedUsers() { return deletedUsers; }

    public long getImageTotalFiles() { return imageTotalFiles; }
    public long getImageTotalBytes() { return imageTotalBytes; }

    public long getVideoTotalFiles() { return videoTotalFiles; }
    public long getVideoTotalBytes() { return videoTotalBytes; }

    public long getTotalStorageBytes() { return totalStorageBytes; }
}
