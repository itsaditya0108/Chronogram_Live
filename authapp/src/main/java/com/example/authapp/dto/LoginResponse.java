package com.example.authapp.dto;

public class LoginResponse {

    private String accessToken;
    private String refreshToken;

    private Long userId;
    private String name;
    private String email;
    private String phone;
    private boolean emailVerified;
    private boolean phoneVerified;
    private String status;
    private boolean isAdmin;
    private String adminAccessToken;

    // ✅ FULL LOGIN CONSTRUCTOR
    public LoginResponse(
            String accessToken,
            String refreshToken,
            Long userId,
            String name,
            String email,
            String phone,
            boolean emailVerified,
            boolean phoneVerified,
            String status,
            boolean isAdmin,
            String adminAccessToken) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.userId = userId;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.emailVerified = emailVerified;
        this.phoneVerified = phoneVerified;
        this.status = status;
        this.isAdmin = isAdmin;
        this.adminAccessToken = adminAccessToken;
    }

    // ✅ REFRESH TOKEN CONSTRUCTOR (ACCESS TOKEN ONLY)
    public LoginResponse(String accessToken) {
        this.accessToken = accessToken;
    }

    public boolean isAdmin() {
        return isAdmin;
    }

    public String getAdminAccessToken() {
        return adminAccessToken;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public Long getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public boolean isPhoneVerified() {
        return phoneVerified;
    }

    public String getStatus() {
        return status;
    }
}
