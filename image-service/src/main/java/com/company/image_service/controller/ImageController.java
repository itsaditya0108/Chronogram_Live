package com.company.image_service.controller;

import com.company.image_service.dto.ImageBulkUploadResponseDto;
import com.company.image_service.dto.ImageResponseDto;
import com.company.image_service.service.IImageService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api/images")
public class ImageController {

    private final IImageService imageService;

    @Autowired
    public ImageController(IImageService imageService) {
        this.imageService = imageService;
    }

    private Long getUserId(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            throw new RuntimeException("User not authenticated");
        }
        return userId;
    }

    /**
     * POST /api/images/bulk (As per TESTING_GUIDE.md)
     */
    @PostMapping("/bulk")
    public ResponseEntity<ImageBulkUploadResponseDto> bulkUpload(
            HttpServletRequest request,
            @RequestParam("files") MultipartFile[] files,
            @RequestParam(value = "type", defaultValue = "personal") String type) {

        Long userId = getUserId(request);
        if (files == null || files.length == 0) {
            return ResponseEntity.badRequest().build();
        }

        ImageBulkUploadResponseDto response = imageService.executeBulkUpload(userId, files, type);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/images (As per TESTING_GUIDE.md)
     */
    @GetMapping
    public ResponseEntity<Page<ImageResponseDto>> getImages(
            HttpServletRequest request,
            @RequestParam(value = "type", defaultValue = "all") String type,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        
        Long userId = getUserId(request);
        return ResponseEntity.ok(imageService.getUserImages(userId, type, PageRequest.of(page, size)));
    }

    /**
     * GET /api/images/{imageId}/download (As per TESTING_GUIDE.md)
     */
    @GetMapping("/{imageId}/download")
    public ResponseEntity<Resource> downloadImage(
            HttpServletRequest request,
            @PathVariable Long imageId) {
        Long userId = getUserId(request);
        return ResponseEntity.ok(imageService.downloadImage(imageId, userId));
    }

    /**
     * GET /api/images/{imageId}/thumbnail (As per TESTING_GUIDE.md)
     */
    @GetMapping("/{imageId}/thumbnail")
    public ResponseEntity<Resource> downloadThumbnail(
            HttpServletRequest request,
            @PathVariable Long imageId) {
        Long userId = getUserId(request);
        return ResponseEntity.ok(imageService.downloadThumbnail(imageId, userId));
    }

    /**
     * GET /api/images/{imageId}/stream (As per TESTING_GUIDE.md)
     */
    @GetMapping("/{imageId}/stream")
    public ResponseEntity<StreamingResponseBody> streamImage(
            HttpServletRequest request,
            @PathVariable Long imageId,
            @RequestParam(value = "thumbnail", defaultValue = "false") boolean thumbnail) {
        Long userId = getUserId(request);
        StreamingResponseBody body = imageService.streamDecryptedImage(imageId, userId, thumbnail);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(body);
    }

    /**
     * GET /api/images/{imageId}/variants/{type} (As per TESTING_GUIDE.md)
     */
    @GetMapping("/{imageId}/variants/{type}")
    public ResponseEntity<Resource> getVariant(
            HttpServletRequest request,
            @PathVariable Long imageId,
            @PathVariable String type) {
        Long userId = getUserId(request);
        // This is a simple implementation; real variants might need more logic
        String path = imageService.resolveVariantPath(imageId, type);
        // Note: resolveVariantPath just returns a path. We might need a better way to stream it.
        // For now, redirecting to existing download if it's main or thumb
        if ("thumb".equalsIgnoreCase(type) || "thumbnail".equalsIgnoreCase(type)) {
            return downloadThumbnail(request, imageId);
        }
        return downloadImage(request, imageId);
    }
}
