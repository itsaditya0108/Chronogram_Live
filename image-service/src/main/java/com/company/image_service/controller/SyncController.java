package com.company.image_service.controller;

import com.company.image_service.entity.SyncSession;
import com.company.image_service.entity.UploadSession;
import com.company.image_service.service.ChunkUploadService;
import com.company.image_service.service.MergeWorkerService;
import com.company.image_service.service.SyncManagerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.http.HttpServletRequest;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/sync")
public class SyncController {

    private final SyncManagerService syncManager;
    private final ChunkUploadService chunkUploadService;
    private final MergeWorkerService mergeWorkerService;

    @Autowired
    public SyncController(SyncManagerService syncManager,
            ChunkUploadService chunkUploadService,
            MergeWorkerService mergeWorkerService) {
        this.syncManager = syncManager;
        this.chunkUploadService = chunkUploadService;
        this.mergeWorkerService = mergeWorkerService;
    }

    /**
     * Attempts to start a sync session.
     */
    @PostMapping("/init")
    public ResponseEntity<?> initSync(HttpServletRequest request,
            @RequestParam SyncSession.TriggerType triggerType) {
        try {
            Long userId = (Long) request.getAttribute("userId");
            if (userId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
            }

            SyncSession session = syncManager.startSyncSession(userId, triggerType);
            return ResponseEntity.ok(session);
        } catch (IllegalStateException e) {
            Map<String, String> err = new HashMap<>();
            err.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(err);
        }
    }

    /**
     * Marks a sync session complete.
     */
    @PostMapping("/{sessionId}/complete")
    public ResponseEntity<?> completeSync(@PathVariable Long sessionId,
            @RequestParam int detected,
            @RequestParam int skipped) {
        syncManager.completeSyncSession(sessionId, detected, skipped);
        return ResponseEntity.ok().build();
    }

    /**
     * Initiates a chunked file upload.
     */
    @PostMapping("/upload/init")
    public ResponseEntity<?> initUpload(HttpServletRequest request,
            @RequestParam String originalFilename,
            @RequestParam int totalChunks,
            @RequestParam long totalFileSize,
            @RequestParam(required = false) Long syncSessionId) {

        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
        }

        if (!syncManager.canStartUpload(userId, totalFileSize)) {
            Map<String, String> err = new HashMap<>();
            err.put("error", "Upload quota or active upload limit exceeded.");
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(err);
        }

        UploadSession session = chunkUploadService.initiateUpload(userId, originalFilename, totalChunks, totalFileSize,
                syncSessionId);
        return ResponseEntity.ok(session);
    }

    /**
     * Receives a specific chunk. If it's the final chunk, triggers the async merge
     * worker.
     */
    @PostMapping("/upload/{uploadId}/chunk")
    public ResponseEntity<?> uploadChunk(@PathVariable String uploadId,
            @RequestParam("chunk") MultipartFile chunk,
            @RequestParam int chunkIndex) {
        try {
            boolean isComplete = chunkUploadService.receiveChunk(uploadId, chunk, chunkIndex);

            if (isComplete) {
                // Submit to the thread pool for merging and encrypting
                mergeWorkerService.processUploadSessionAsync(uploadId);
                return ResponseEntity
                        .ok(Map.of("message", "All chunks received. Merging and encrypting in background."));
            }

            return ResponseEntity.ok(Map.of("message", "Chunk " + chunkIndex + " received successfully"));

        } catch (Exception e) {
            Map<String, String> err = new HashMap<>();
            err.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(err);
        }
    }

    /**
     * Checks the status of an upload.
     */
    @GetMapping("/upload/{uploadId}/status")
    public ResponseEntity<?> getUploadStatus(@PathVariable String uploadId) {
        UploadSession session = chunkUploadService.getSessionStatus(uploadId);
        return ResponseEntity.ok(session);
    }
}
