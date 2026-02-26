package com.example.authapp.dto.admin;

public class AdminLoginResponse {

    private Long adminId;
    private String username;
    private String role;
    private String token;

    public AdminLoginResponse(Long adminId, String username, String role, String token) {
        this.adminId = adminId;
        this.username = username;
        this.role = role;
        this.token = token;
    }

    public Long getAdminId() { return adminId; }
    public String getUsername() { return username; }
    public String getRole() { return role; }
    public String getToken() { return token; }
}
