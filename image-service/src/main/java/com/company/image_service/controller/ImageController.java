package com.company.image_service.controller; // Package for REST controllers

import com.company.image_service.dto.ImageResponseDto; // DTO for image response
import com.company.image_service.dto.PageResponseDto; // DTO for paginated response
import com.company.image_service.entity.Image; // Image entity
import com.company.image_service.mapper.ImageMapper; // Mapper for converting entities to DTOs
import com.company.image_service.service.ImageService; // Service for image operations
import jakarta.servlet.http.HttpServletRequest; // HTTP Request object
import org.springframework.core.io.Resource; // Resource interface for file downloads
import org.springframework.data.domain.Page; // Page interface for pagination
import org.springframework.data.domain.Pageable; // Pageable interface for pagination info
import org.springframework.data.domain.Sort; // Sort options
import org.springframework.data.web.PageableDefault; // Default pagination settings annotation
import org.springframework.http.HttpHeaders; // HTTP Headers
import org.springframework.http.MediaType; // Media Type constants
import org.springframework.http.ResponseEntity; // HTTP Response Entity
import org.springframework.web.bind.annotation.*; // Spring Web annotations
import org.springframework.web.multipart.MultipartFile; // Multipart file support

import java.util.List; // List utility

@RestController // Marks this class as a REST controller
@RequestMapping("/api/images") // Base URL path for image endpoints
public class ImageController {

    private final ImageService imageService; // Service dependency

    // Constructor injection for ImageService
    public ImageController(ImageService imageService) {
        this.imageService = imageService;
    }

    // ----------------------------
    // Single upload
    // ----------------------------
    /**
     * Endpoint to upload a single image.
     * URL: POST /api/images
     */
    @PostMapping
    public ImageResponseDto upload(
            @RequestParam("file") MultipartFile file, // The image file
            @RequestParam(value = "type", defaultValue = "personal") String type, // Image type (default: personal)
            HttpServletRequest request) {

        try {
            // Debug logging for upload request
            System.out.println(
                    "DEBUG: Upload request received. Type: " + type + ", UserId: " + request.getAttribute("userId"));

            // Extract user ID from request attributes (set by JwtAuthenticationFilter)
            Long userId = (Long) request.getAttribute("userId");
            if (userId == null) {
                throw new RuntimeException("User ID not found in request. Auth filter might be failing.");
            }

            // Call service to upload image
            Image image = imageService.uploadImage(userId, file, type);
            // Convert result to DTO and return
            return ImageMapper.toDto(image);

        } catch (Exception e) {
            e.printStackTrace(); // Log stack trace for debugging
            throw new RuntimeException("Upload failed: " + e.getMessage(), e); // Re-throw as RuntimeException
        }
    }

    // ----------------------------
    // Multi upload
    // ----------------------------
    /**
     * Endpoint to upload multiple images at once.
     * URL: POST /api/images/bulk
     */
    @PostMapping("/bulk")
    public ResponseEntity<List<Image>> uploadImages(
            @RequestParam("files") List<MultipartFile> files, // List of image files
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId"); // Get authenticated user ID

        // Call service to upload multiple images and return the list of created
        // entities
        return ResponseEntity.ok(
                imageService.uploadImages(userId, files));
    }

    // ----------------------------
    // List images
    // ----------------------------
    // @GetMapping
    // public Page<ImageResponseDto> getImages(
    // Pageable pageable,
    // HttpServletRequest request
    // ) {
    // Long userId = (Long) request.getAttribute("userId");
    //
    // return imageService.getUserImages(userId, pageable)
    // .map(ImageMapper::toDto);
    // }

    /**
     * Endpoint to retrieve a paginated list of user's images.
     * URL: GET /api/images
     * Supports filtering by 'type' (e.g., 'personal', 'chat', 'all').
     */
    @GetMapping
    public PageResponseDto<ImageResponseDto> getImages(
            @RequestParam(required = false, defaultValue = "all") String type, // Filter param: 'all', 'personal',
                                                                               // 'chat'
            @PageableDefault(size = 20, sort = "createdTimestamp", direction = Sort.Direction.DESC) Pageable pageable, // Default
                                                                                                                       // pagination:
                                                                                                                       // 20
                                                                                                                       // items,
                                                                                                                       // newest
                                                                                                                       // first
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId"); // Get authenticated user ID

        // Fetch paginated images from service
        Page<ImageResponseDto> page = imageService
                .getUserImages(userId, type, pageable) // Pass filter type and pagination info
                .map(ImageMapper::toDto); // Convert entities to DTOs

        // Return structured page response
        return new PageResponseDto<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast());
    }

    // ----------------------------
    // Download image
    // ----------------------------
    // @GetMapping("/{id}/download")
    // public ResponseEntity<Resource> download(
    // @PathVariable Long id,
    // HttpServletRequest request
    // ) {
    // Long userId = (Long) request.getAttribute("userId");
    //
    // Image image = imageService.getUserImage(id, userId);
    // Resource resource = imageService.downloadImage(id, userId);
    //
    // return ResponseEntity.ok()
    // .contentType(MediaType.parseMediaType(image.getContentType()))
    // .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
    // .body(resource);
    // }

    @GetMapping("/{id}/download")
    public ResponseEntity<org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody> download(
            @PathVariable Long id) {
        // DEV: no userId, no ownership check
        Image image = imageService.getImageById(id); // Retrieve image metadata

        org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody stream = imageService
                .streamDecryptedImage(id, image.getUserId(), false);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.getContentType())) // Set correct MIME type
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline") // Display inline (browser handles rendering)
                .body(stream);
    }

    /**
     * Endpoint to soft-delete an image.
     * URL: DELETE /api/images/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId"); // Get authenticated user ID

        if (userId == null) {
            throw new RuntimeException("Unauthorized"); // Security check
        }

        imageService.softDeleteImage(id, userId); // Perform soft delete
        return ResponseEntity.noContent().build(); // Return 204 No Content
    }

    @GetMapping("/{id}/thumbnail")
    public ResponseEntity<org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody> downloadThumbnail(
            @PathVariable Long id,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            throw new RuntimeException("Unauthorized");
        }

        org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody stream = imageService
                .streamDecryptedImage(id, userId, true);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "image/*") // Set generic image content type
                .body(stream);
    }

    /**
     * Endpoint to validate if an image exists and belongs to the user.
     * URL: GET /api/images/{id}/validate
     */
    @GetMapping("/{id}/validate")
    public ResponseEntity<java.util.Map<String, Boolean>> validateImage(
            @PathVariable Long id,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(401).build(); // Return 401 Unauthorized
        }

        boolean isValid = imageService.validateImage(id, userId); // Check validity
        if (!isValid) {
            return ResponseEntity.status(404).body(java.util.Map.of("valid", false)); // Return 404 if invalid
        }

        return ResponseEntity.ok(java.util.Map.of("valid", true)); // Return 200 OK if valid
    }

}
