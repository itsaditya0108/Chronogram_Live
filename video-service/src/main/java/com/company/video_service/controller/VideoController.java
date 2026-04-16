package com.company.video_service.controller;

import com.company.video_service.entity.Video;
import com.company.video_service.repository.VideoRepository;
import com.company.video_service.service.EncryptionService;
import com.company.video_service.service.storage.FileStorageService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.List;

@RestController
@RequestMapping("/api/v1/videos")
public class VideoController {

    private static final Logger log = LoggerFactory.getLogger(VideoController.class);

    private final VideoRepository videoRepository;
    private final com.company.video_service.repository.VideoProcessingJobRepository jobRepository;
    private final EncryptionService encryptionService;
    private final FileStorageService fileStorageService;

    public VideoController(VideoRepository videoRepository,
                        com.company.video_service.repository.VideoProcessingJobRepository jobRepository,
                        EncryptionService encryptionService,
                        FileStorageService fileStorageService) {
        this.videoRepository = videoRepository;
        this.jobRepository = jobRepository;
        this.encryptionService = encryptionService;
        this.fileStorageService = fileStorageService;
    }

    @GetMapping("/processing-jobs")
    public ResponseEntity<List<com.company.video_service.entity.VideoProcessingJob>> getProcessingJobs(
            @RequestAttribute("userId") Long userId) {
        return ResponseEntity.ok(jobRepository.findAllByUserIdAndStatusIn(
                userId,
                List.of(com.company.video_service.entity.VideoProcessingJobStatus.PENDING,
                        com.company.video_service.entity.VideoProcessingJobStatus.RUNNING)));
    }

    @GetMapping
    public ResponseEntity<List<Video>> getAllVideos(@RequestAttribute("userId") Long userId) {
        return ResponseEntity.ok(
                videoRepository.findAllByUserIdAndIsDeletedFalseOrderByCreatedTimestampDesc(userId));
    }

    /**
     * SECURE STREAMING: Fetches encrypted video from S3 and decrypts on-the-fly.
     */
    @GetMapping("/{videoUid}")
    public ResponseEntity<StreamingResponseBody> streamVideo(
            @RequestAttribute("userId") Long userId,
            @PathVariable String videoUid) {
        
        Video video = videoRepository.findByVideoUid(videoUid)
                .orElseThrow(() -> new RuntimeException("VIDEO_NOT_FOUND"));

        // Security check: Ensure user owns the video
        if (!video.getUserId().equals(userId)) {
            throw new RuntimeException("FORBIDDEN");
        }

        log.info("[STREAM] Decrypting and streaming video: {} for User: {}", videoUid, userId);

        StreamingResponseBody responseBody = outputStream -> {
            try (InputStream encryptedStream = fileStorageService.loadAsResource(video.getOriginalFilePath()).getInputStream()) {
                encryptionService.decryptToStream(encryptedStream, outputStream);
            } catch (Exception e) {
                log.error("[STREAM] Decryption failed for video: {}", videoUid, e);
            }
        };

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, video.getMimeType() != null ? video.getMimeType() : "video/mp4")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + videoUid + ".mp4\"")
                .body(responseBody);
    }

    /**
     * SECURE THUMBNAIL: Fetches encrypted thumbnail from S3 and decrypts on-the-fly.
     */
    @GetMapping("/{videoUid}/thumbnail")
    public ResponseEntity<StreamingResponseBody> getThumbnail(
            @RequestAttribute("userId") Long userId,
            @PathVariable String videoUid) {
        
        Video video = videoRepository.findByVideoUid(videoUid)
                .orElseThrow(() -> new RuntimeException("VIDEO_NOT_FOUND"));

        if (!video.getUserId().equals(userId)) {
            throw new RuntimeException("FORBIDDEN");
        }

        if (video.getThumbnailFilePath() == null) {
            return ResponseEntity.notFound().build();
        }

        log.info("[STREAM] Decrypting and streaming thumbnail for video: {}", videoUid);

        StreamingResponseBody responseBody = outputStream -> {
            try (InputStream encryptedStream = fileStorageService.loadAsResource(video.getThumbnailFilePath()).getInputStream()) {
                encryptionService.decryptToStream(encryptedStream, outputStream);
            } catch (Exception e) {
                log.error("[STREAM] Decryption failed for thumbnail: {}", videoUid, e);
            }
        };

        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .body(responseBody);
    }
}
