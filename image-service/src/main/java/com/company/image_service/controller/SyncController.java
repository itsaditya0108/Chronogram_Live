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
import com.company.image_service.exception.UnauthorizedException;
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
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            throw new UnauthorizedException("User not authenticated");
        }

        SyncSession session = syncManager.startSyncSession(userId, triggerType);
        return ResponseEntity.ok(session);
    }

    /**
     * Marks a sync session complete.
     */
    @PostMapping("/{sessionId}/complete")
    public ResponseEntity<?> completeSync(@PathVariable Long sessionId,
            @RequestParam("filesDetected") int filesDetected,
            @RequestParam("filesSkipped") int filesSkipped) {
        syncManager.completeSyncSession(sessionId, filesDetected, filesSkipped);
        return ResponseEntity.ok().build();
    }

    /**
     * Initiates a chunked file upload.
     */
    @PostMapping("/upload/init")
    public ResponseEntity<?> initUpload(HttpServletRequest request,
            @RequestBody com.company.image_service.dto.UploadInitRequest uploadRequest) {

        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            throw new UnauthorizedException("User not authenticated");
        }

        if (!syncManager.canStartUpload(userId, uploadRequest.getTotalFileSize())) {
            throw new IllegalStateException("Upload quota or active upload limit exceeded");
        }

        com.company.image_service.dto.ImageUploadInitResponse response = chunkUploadService.initiateUpload(
                userId,
                uploadRequest.getOriginalFilename(),
                uploadRequest.getTotalChunks(),
                uploadRequest.getTotalFileSize(),
                uploadRequest.getSyncSessionId(),
                uploadRequest.getContentHash());
        return ResponseEntity.ok(response);
    }

    /**
     * Receives a specific chunk. If it's the final chunk, triggers the async merge
     * worker.
     */
    @PostMapping("/upload/{uploadId}/chunk")
    public ResponseEntity<?> uploadChunk(@PathVariable String uploadId,
            @RequestHeader(value = "X-Chunk-Index", required = false) Integer headerIndex,
            @RequestParam(value = "chunkIndex", required = false) Integer paramIndex,
            @RequestBody byte[] chunkData) {
        int chunkIndex = (headerIndex != null) ? headerIndex : (paramIndex != null ? paramIndex : 0);

        boolean isComplete = chunkUploadService.receiveChunkBytes(uploadId, chunkData, chunkIndex);

        if (isComplete) {
            mergeWorkerService.processUploadSessionAsync(uploadId);
            return ResponseEntity
                    .ok(Map.of("message", "All chunks received. Merging and encrypting in background."));
        }

        return ResponseEntity.ok(Map.of("message", "Chunk " + chunkIndex + " received successfully"));
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
