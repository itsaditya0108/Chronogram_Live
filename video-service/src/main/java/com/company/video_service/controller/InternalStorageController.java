package com.company.video_service.controller;

import com.company.video_service.dto.StorageSummaryResponse;
import com.company.video_service.dto.UserStorageResponse;
import com.company.video_service.service.StorageService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/storage")
public class InternalStorageController {

    private final StorageService storageService;

    public InternalStorageController(StorageService storageService) {
        this.storageService = storageService;
    }

    @GetMapping("/summary")
    public ResponseEntity<StorageSummaryResponse> summary() {
        return ResponseEntity.ok(storageService.getSummary());
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<UserStorageResponse> userStorage(@PathVariable Long userId) {
        return ResponseEntity.ok(storageService.getUserStorage(userId));
    }
}
