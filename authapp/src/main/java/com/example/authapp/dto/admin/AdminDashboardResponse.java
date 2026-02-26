package com.example.authapp.dto.admin;

public class AdminDashboardResponse {

    private long totalUsers;
    private long activeUsers;
    private long inactiveUsers;
    private long blockedUsers;
    private long deletedUsers;

    public AdminDashboardResponse(long totalUsers, long activeUsers, long inactiveUsers,
                                  long blockedUsers, long deletedUsers) {
        this.totalUsers = totalUsers;
        this.activeUsers = activeUsers;
        this.inactiveUsers = inactiveUsers;
        this.blockedUsers = blockedUsers;
        this.deletedUsers = deletedUsers;
    }

    public long getTotalUsers() { return totalUsers; }
    public long getActiveUsers() { return activeUsers; }
    public long getInactiveUsers() { return inactiveUsers; }
    public long getBlockedUsers() { return blockedUsers; }
    public long getDeletedUsers() { return deletedUsers; }
}
