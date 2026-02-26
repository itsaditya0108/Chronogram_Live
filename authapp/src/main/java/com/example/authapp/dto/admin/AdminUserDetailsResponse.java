package com.example.authapp.dto.admin;

import java.time.LocalDateTime;

public class AdminUserDetailsResponse {

    private Long userId;
    private String name;
    private String email;
    private String phone;

    private boolean emailVerified;
    private boolean phoneVerified;

    private String status;

    private int failedLoginAttempts;
    private LocalDateTime lockedUntil;

    private LocalDateTime createdTimestamp;
    private LocalDateTime updatedAt;

    public AdminUserDetailsResponse(Long userId, String name, String email, String phone,
                                    boolean emailVerified, boolean phoneVerified,
                                    String status, int failedLoginAttempts,
                                    LocalDateTime lockedUntil,
                                    LocalDateTime createdTimestamp,
                                    LocalDateTime updatedAt) {
        this.userId = userId;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.emailVerified = emailVerified;
        this.phoneVerified = phoneVerified;
        this.status = status;
        this.failedLoginAttempts = failedLoginAttempts;
        this.lockedUntil = lockedUntil;
        this.createdTimestamp = createdTimestamp;
        this.updatedAt = updatedAt;
    }

    public Long getUserId() { return userId; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }

    public boolean isEmailVerified() { return emailVerified; }
    public boolean isPhoneVerified() { return phoneVerified; }

    public String getStatus() { return status; }

    public int getFailedLoginAttempts() { return failedLoginAttempts; }
    public LocalDateTime getLockedUntil() { return lockedUntil; }

    public LocalDateTime getCreatedTimestamp() { return createdTimestamp; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
