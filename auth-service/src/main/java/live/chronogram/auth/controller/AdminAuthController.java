package live.chronogram.auth.controller;

import live.chronogram.auth.dto.AdminLoginRequest;
import live.chronogram.auth.dto.TokenResponse;
import live.chronogram.auth.service.AdminService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/api/auth/admin")
public class AdminAuthController {

    @Autowired
    private AdminService adminService;


    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@RequestBody AdminLoginRequest request, HttpServletResponse response) {
        TokenResponse tokenResponse = adminService.login(request.getUsername(), request.getPassword());
        
        // Prepare Cookie for Production Security
        Cookie cookie = new Cookie("adminToken", tokenResponse.getAccessToken());
        cookie.setHttpOnly(true);
        cookie.setSecure(true); // Should be true for production (HTTPS)
        cookie.setPath("/");
        cookie.setMaxAge(12 * 60 * 60); // 12 hours
        response.addCookie(cookie);
        
        return ResponseEntity.ok(tokenResponse);
    }

    @PostMapping("/register")
    // Note: In production, this should be secured so only SUPER_ADMIN can create other admins
    public ResponseEntity<Void> register(@RequestParam String username, @RequestParam String email, 
                                       @RequestParam String password, @RequestParam String role) {
        adminService.createAdmin(username, email, password, role);
        return ResponseEntity.ok().build();
    }
}
