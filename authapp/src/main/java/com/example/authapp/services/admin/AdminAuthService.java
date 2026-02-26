package com.example.authapp.services.admin;

import com.example.authapp.dto.admin.AdminLoginRequest;
import com.example.authapp.dto.admin.AdminLoginResponse;
import com.example.authapp.entity.Admin;
import com.example.authapp.repository.AdminRepository;
import com.example.authapp.services.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class AdminAuthService {

    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AdminAuthService(AdminRepository adminRepository,
                            PasswordEncoder passwordEncoder,
                            JwtService jwtService) {
        this.adminRepository = adminRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public AdminLoginResponse login(AdminLoginRequest request) {

        Admin admin = adminRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new RuntimeException("Invalid credentials"));

        if (!Boolean.TRUE.equals(admin.getIsActive())) {
            throw new RuntimeException("Admin account disabled");
        }

        if (admin.getLockedUntil() != null && admin.getLockedUntil().isAfter(LocalDateTime.now())) {
            throw new RuntimeException("Admin account locked");
        }

        boolean ok = passwordEncoder.matches(request.getPassword(), admin.getPasswordHash());
        if (!ok) {
            throw new RuntimeException("Invalid credentials");
        }

        String token = jwtService.generateAdminToken(admin.getAdminId(), admin.getRole());

        return new AdminLoginResponse(
                admin.getAdminId(),
                admin.getUsername(),
                admin.getRole(),
                token
        );
    }
}
