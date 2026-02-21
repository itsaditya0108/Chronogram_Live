package com.company.image_service.controller; // Package for REST controllers

import com.company.image_service.entity.ProfilePicture; // Entity for Profile Picture
import com.company.image_service.service.ProfilePictureService; // Service for profile picture operations
import jakarta.servlet.http.HttpServletRequest; // HTTP Request object
import org.springframework.core.io.Resource; // Resource interface for file downloads
import org.springframework.http.ResponseEntity; // HTTP Response Entity
import org.springframework.web.bind.annotation.*; // Spring Web annotations
import org.springframework.web.multipart.MultipartFile; // Multipart file support

import java.util.List; // List utility

@RestController // Marks this class as a REST controller
@RequestMapping("/api/profile-picture") // Base URL path for profile picture endpoints
public class ProfilePictureController {

    private final ProfilePictureService service; // Service dependency

    // Constructor injection for ProfilePictureService
    public ProfilePictureController(ProfilePictureService service) {
        this.service = service;
    }

    /**
     * Endpoint to upload a new profile picture.
     * URL: POST /api/profile-picture
     */
    @PostMapping
    public ResponseEntity<ProfilePicture> upload(
            @RequestParam("file") MultipartFile file, // The image file
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId"); // Get authenticated user ID
        // Delegate to service to handle upload and return the created entity
        return ResponseEntity.ok(service.upload(userId, file));
    }

    /**
     * Endpoint to get the history of profile pictures for the authenticated user.
     * URL: GET /api/profile-picture/history
     */
    @GetMapping("/history")
    public ResponseEntity<List<ProfilePicture>> getHistory(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId"); // Get authenticated user ID
        // Return list of previous profile pictures
        return ResponseEntity.ok(service.getHistory(userId));
    }

    /**
     * Endpoint to view a specific profile picture (original, small, medium).
     * URL: GET /api/profile-picture/{profilePictureId}/view/{type}
     */
    @GetMapping("/{profilePictureId}/view/{type}")
    public ResponseEntity<Resource> getView(
            @PathVariable Long profilePictureId,
            @PathVariable String type, // 'original', 'small', 'medium'
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId"); // Get authenticated user ID
        // Return the file resource
        return ResponseEntity.ok(service.getProfilePictureResource(userId, profilePictureId, type));
    }

    /**
     * Endpoint to get the current user's *active* small profile picture
     * (thumbnail).
     * URL: GET /api/profile-picture/small
     */
    @GetMapping("/small")
    public ResponseEntity<Resource> small(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return ResponseEntity.ok(service.getSmall(userId));
    }

    /**
     * Endpoint to get the current user's *active* medium profile picture.
     * URL: GET /api/profile-picture/medium
     */
    @GetMapping("/medium")
    public ResponseEntity<Resource> medium(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return ResponseEntity.ok(service.getMedium(userId));
    }

    /**
     * Endpoint to get *another user's* active small profile picture (public access
     * usually).
     * URL: GET /api/profile-picture/{userId}/small
     */
    @GetMapping("/{userId}/small")
    public ResponseEntity<Resource> getSmall(@PathVariable Long userId) {
        return ResponseEntity.ok(service.getSmall(userId));
    }

    /**
     * Endpoint to get *another user's* active medium profile picture.
     * URL: GET /api/profile-picture/{userId}/medium
     */
    @GetMapping("/{userId}/medium")
    public ResponseEntity<Resource> getMedium(@PathVariable Long userId) {
        return ResponseEntity.ok(service.getMedium(userId));
    }

    /**
     * Endpoint to delete the current active profile picture (or reset to default).
     * URL: DELETE /api/profile-picture
     */
    @DeleteMapping
    public ResponseEntity<Void> delete(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        service.delete(userId); // Perform deletion logic
        return ResponseEntity.noContent().build(); // Return 204 No Content
    }
}
