package com.company.video_service.controller; // Package for REST controllers

import com.company.video_service.entity.Video; // Video entity
import com.company.video_service.repository.VideoRepository; // Video repository
import org.springframework.core.io.FileSystemResource; // Resource implementation for file system
import org.springframework.core.io.Resource; // Resource interface
import org.springframework.http.HttpHeaders; // HTTP Headers
import org.springframework.http.ResponseEntity; // HTTP Response Entity
import org.springframework.web.bind.annotation.GetMapping; // GetMapping annotation
import org.springframework.web.bind.annotation.PathVariable; // PathVariable annotation
import org.springframework.web.bind.annotation.RestController; // RestController annotation
import org.slf4j.Logger; // SLF4J Logger
import org.slf4j.LoggerFactory; // SLF4J LoggerFactory

import java.io.File; // File IO class

@RestController // Marks this class as a REST controller
@org.springframework.web.bind.annotation.CrossOrigin(origins = "*") // Allow CORS from all origins (adjust for
                                                                    // production)
public class VideoThumbnailController {

    private static final Logger log = LoggerFactory.getLogger(VideoThumbnailController.class); // Logger instance

    private final VideoRepository videoRepository; // Repository for Video entity

    @org.springframework.beans.factory.annotation.Value("${video.storage.final-path}") // Inject final storage path from
                                                                                       // properties
    private String finalStoragePath;

    private final com.company.video_service.repository.VideoThumbnailRepository thumbnailRepository; // Repository for
                                                                                                     // VideoThumbnail
                                                                                                     // entity

    // Constructor injection for repositories
    public VideoThumbnailController(VideoRepository videoRepository,
            com.company.video_service.repository.VideoThumbnailRepository thumbnailRepository) {
        this.videoRepository = videoRepository;
        this.thumbnailRepository = thumbnailRepository;
    }

    /**
     * Endpoint to retrieve the thumbnail image for a specific video.
     * URL: /api/v1/videos/{videoUid}/thumbnail
     */
    @GetMapping("/api/v1/videos/{videoUid}/thumbnail")
    public ResponseEntity<Resource> getThumbnail(@PathVariable String videoUid) {
        log.debug("Fetching thumbnail for videoUid={}", videoUid); // Debug log

        String thumbPath = null;

        // 1. Try to find the thumbnail in the specific `video_thumbnails` table first
        // This is the preferred way if we have multiple thumbnails or generated ones
        var vt = thumbnailRepository.findFirstByVideoUidAndIsDefaultTrue(videoUid);
        if (vt.isPresent()) {
            thumbPath = vt.get().getThumbnailPath(); // Get path from entity
            log.debug("Found in VideoThumbnail entity: {}", thumbPath);
        } else {
            // 2. Fallback to `Video` entity's `thumbnailFilePath` column
            // This supports legacy records or simple setups where only one thumb is tracked
            // directly on the video
            log.debug("Not found in VideoThumbnail entity, falling back to Video entity.");
            Video video = videoRepository.findByVideoUid(videoUid)
                    .orElseThrow(() -> new RuntimeException("VIDEO_NOT_FOUND")); // Throw 404 if video doesn't exist

            if (video.getThumbnailFilePath() == null) {
                log.warn("Video entity has no thumbnail path for videoUid={}", videoUid);
                throw new RuntimeException("THUMBNAIL_NOT_READY"); // Throw custom exception if not generated yet
            }
            thumbPath = video.getThumbnailFilePath();
            log.debug("Found in Video entity: {}", thumbPath);
        }

        File file;
        // Check if the stored path is absolute or relative
        if (new File(thumbPath).isAbsolute()) {
            file = new File(thumbPath); // Use absolute path directly works
        } else {
            // Combine configured storage root with relative path
            file = new File(finalStoragePath, thumbPath);
        }

        log.debug("Resolved file path: {}", file.getAbsolutePath());

        // Verify the file actually exists on the disk
        if (!file.exists()) {
            log.error("File does not exist on disk: {}", file.getAbsolutePath());
            throw new RuntimeException("THUMBNAIL_FILE_NOT_FOUND");
        }

        // Wrap file in a FileSystemResource
        Resource resource = new FileSystemResource(file);

        // Return 200 OK with the image resource and Content-Type header
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "image/jpeg") // Assuming JPEG thumbnails
                .body(resource);
    }

    /**
     * Endpoint to upload an encrypted thumbnail blob directly from the client.
     */
    @org.springframework.web.bind.annotation.PostMapping(value = "/api/v1/videos/{videoUid}/thumbnail")
    public ResponseEntity<String> uploadThumbnail(
            @org.springframework.web.bind.annotation.RequestHeader("X-USER-ID") Long userId,
            @PathVariable String videoUid,
            @org.springframework.web.bind.annotation.RequestBody byte[] thumbnailBytes) {

        Video video = videoRepository.findByVideoUid(videoUid)
                .orElseThrow(() -> new RuntimeException("VIDEO_NOT_FOUND"));

        if (!video.getUserId().equals(userId)) {
            throw new RuntimeException("FORBIDDEN");
        }

        File originalFile = new File(finalStoragePath, video.getOriginalFilePath());
        File folder = originalFile.getParentFile();
        if (!folder.exists()) {
            folder.mkdirs();
        }

        File thumbnailFile = new File(folder, "thumbnail.jpg");
        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(thumbnailFile)) {
            fos.write(thumbnailBytes);
        } catch (java.io.IOException e) {
            throw new RuntimeException("FAILED_TO_SAVE_THUMBNAIL", e);
        }

        String relativeThumbPath = video.getOriginalFilePath().replace("original.mp4", "thumbnail.jpg");
        video.setThumbnailFilePath(relativeThumbPath);
        video.setThumbnailStatus("READY");
        video.setThumbnailGeneratedTimestamp(java.time.LocalDateTime.now());
        video.setUpdatedTimestamp(java.time.LocalDateTime.now());
        videoRepository.save(video);

        // Save to VideoThumbnail entity for gallery/previews
        com.company.video_service.entity.VideoThumbnail vt = new com.company.video_service.entity.VideoThumbnail();
        vt.setVideoUid(videoUid);
        vt.setThumbnailUid(java.util.UUID.randomUUID().toString());
        vt.setThumbnailPath(relativeThumbPath);
        vt.setWidth(640);
        vt.setHeight(360);
        vt.setDefault(true);
        thumbnailRepository.saveAndFlush(vt);

        return ResponseEntity.ok("THUMBNAIL_UPLOADED");
    }
}
