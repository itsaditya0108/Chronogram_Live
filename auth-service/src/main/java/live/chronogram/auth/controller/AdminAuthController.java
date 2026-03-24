package live.chronogram.auth.controller;

import live.chronogram.auth.dto.AdminLoginRequest;
import live.chronogram.auth.dto.TokenResponse;
import live.chronogram.auth.service.AdminService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth/admin")
public class AdminAuthController {

    @Autowired
    private AdminService adminService;

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@RequestBody AdminLoginRequest request) {
        return ResponseEntity.ok(adminService.login(request.getUsername(), request.getPassword()));
    }

    @PostMapping("/register")
    // Note: In production, this should be secured so only SUPER_ADMIN can create other admins
    public ResponseEntity<Void> register(@RequestParam String username, @RequestParam String email, 
                                       @RequestParam String password, @RequestParam String role) {
        adminService.createAdmin(username, email, password, role);
        return ResponseEntity.ok().build();
    }
}
