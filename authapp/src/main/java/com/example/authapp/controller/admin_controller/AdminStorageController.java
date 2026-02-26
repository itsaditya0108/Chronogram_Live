package com.example.authapp.controller.admin_controller;

import com.example.authapp.dto.admin.ImageStorageSummaryResponse;
import com.example.authapp.dto.admin.UserImageStorageResponse;
import com.example.authapp.dto.admin.UserVideoStorageResponse;
import com.example.authapp.dto.admin.VideoStorageSummaryResponse;
import com.example.authapp.services.admin.ImageStorageClientService;
import com.example.authapp.services.admin.VideoStorageClientService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/storage")
public class AdminStorageController {

    private final ImageStorageClientService imageStorageClientService;
    private final VideoStorageClientService videoStorageClientService;

    public AdminStorageController(ImageStorageClientService imageStorageClientService, VideoStorageClientService videoStorageClientService) {
        this.imageStorageClientService = imageStorageClientService;
        this.videoStorageClientService = videoStorageClientService;
    }

    @GetMapping("/image/summary")
    public ResponseEntity<ImageStorageSummaryResponse> getImageSummary() {
        return ResponseEntity.ok(imageStorageClientService.getImageStorageSummary());
    }

    @GetMapping("/image/user/{userId}")
    public ResponseEntity<UserImageStorageResponse> getUserImageStorage(@PathVariable Long userId) {
        return ResponseEntity.ok(imageStorageClientService.getUserImageStorage(userId));
    }


    @GetMapping("/video/summary")
    public ResponseEntity<VideoStorageSummaryResponse> getVideoSummary() {
        return ResponseEntity.ok(videoStorageClientService.getVideoStorageSummary());
    }

    @GetMapping("/video/user/{userId}")
    public ResponseEntity<UserVideoStorageResponse> getUserVideoStorage(@PathVariable Long userId) {
        return ResponseEntity.ok(videoStorageClientService.getUserVideoStorage(userId));
    }

}
