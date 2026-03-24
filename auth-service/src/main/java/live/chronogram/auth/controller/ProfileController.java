package live.chronogram.auth.controller;

import jakarta.validation.Valid;
import live.chronogram.auth.dto.ProfileDto;
import live.chronogram.auth.dto.UpdateProfileRequest;
import live.chronogram.auth.service.ProfileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;


/**
 * Controller for managing user profile data and personal identifying information.
 */
@RestController
@RequestMapping("/api/profile")
@CrossOrigin(origins = "*")
public class ProfileController {

    @Autowired
    private ProfileService profileService;

    /**
     * API Endpoint: GET /api/profile
     * Fetches the profile details for the authenticated user.
     */
    @GetMapping
    public ResponseEntity<ProfileDto> getProfile(Authentication authentication) {
        // 1. Extract userId from JWT 'sub' claim
        Long userId = Long.parseLong(authentication.getName());
        
        // 2. Fetch profile data (name, dob, email, mobile)
        return ResponseEntity.ok(profileService.getProfile(userId));
    }

    /**
     * API Endpoint: PUT /api/profile
     * Updates user's personal details (name, dob).
     */
    @PutMapping
    public ResponseEntity<ProfileDto> updateProfile(@Valid @RequestBody UpdateProfileRequest request,
            Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok(profileService.updateProfile(userId, request));
    }


}
