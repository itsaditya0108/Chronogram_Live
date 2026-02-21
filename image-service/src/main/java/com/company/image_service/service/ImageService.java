package com.company.image_service.service; // Package for service interfaces

import com.company.image_service.entity.Image; // Image entity
import org.springframework.core.io.Resource; // Resource interface for file downloads
import org.springframework.data.domain.Page; // Page interface for pagination
import org.springframework.data.domain.Pageable; // Pageable interface
import org.springframework.transaction.annotation.Transactional; // Transaction management (though usually on impl)
import org.springframework.web.multipart.MultipartFile; // Multipart file support

import java.net.MalformedURLException; // Exception for URL issues
import java.util.List; // List utility

public interface ImageService {

    // -----------------------------
    // Upload
    // -----------------------------

    /**
     * Uploads a single image for a user.
     * 
     * @param userId The ID of the user uploading the image.
     * @param file   The image file.
     * @param type   The type of image (e.g., 'personal', 'chat').
     * @return The created Image entity.
     */
    Image uploadImage(Long userId, MultipartFile file, String type);

    /**
     * Uploads multiple images for a user in a batch.
     * 
     * @param userId The ID of the user.
     * @param files  The list of image files.
     * @return A list of created Image entities.
     */
    List<Image> uploadImages(Long userId, List<MultipartFile> files);

    // -----------------------------
    // Read
    // -----------------------------

    /**
     * Retrieves a paginated list of all images for a user.
     * 
     * @deprecated Use getUserImages(Long, String, Pageable) instead for filtering.
     */
    // Old method (can keep or deprecate)
    Page<Image> getUserImages(Long userId, Pageable pageable);

    /**
     * Retrieves a paginated list of images for a user, filtered by type.
     * 
     * @param userId   The user ID.
     * @param type     The image type to filter by (or 'all').
     * @param pageable Pagination information.
     * @return A page of Image entities.
     */
    // New filtered method
    Page<Image> getUserImages(Long userId, String type, Pageable pageable);

    /**
     * Retrieves a specific image by ID and user ID (ownership check).
     * 
     * @param imageId The image ID.
     * @param userId  The user ID.
     * @return The Image entity.
     */
    Image getUserImage(Long imageId, Long userId);

    // -----------------------------
    // Delete (Soft)
    // -----------------------------

    /**
     * Soft-deletes an image (marks as deleted but keeps record).
     * 
     * @param imageId The image ID.
     * @param userId  The user ID.
     * @return The updated Image entity.
     */
    Image softDeleteImage(Long imageId, Long userId);

    // -----------------------------
    // Download
    // -----------------------------

    /**
     * Downloads an image file, verifying ownership.
     * 
     * @param imageId The image ID.
     * @param userId  The user ID.
     * @return The file resource.
     */
    Resource downloadImage(Long imageId, Long userId);

    /**
     * Downloads the thumbnail validation for an image.
     * 
     * @param imageId The image ID.
     * @param userId  The user ID.
     * @return The thumbnail resource.
     */
    Resource downloadThumbnail(Long imageId, Long userId);

    /**
     * Retrieves an image by ID without ownership check (Internal/Admin use).
     * 
     * @param id The image ID.
     * @return The Image entity.
     */
    Image getImageById(Long id);

    /**
     * Downloads an image file by ID without ownership check.
     * 
     * @param id The image ID.
     * @return The file resource.
     */
    Resource downloadImageById(Long id);

    /**
     * Streams the decrypted image directly to the response output.
     */
    org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody streamDecryptedImage(Long imageId,
            Long userId, boolean isThumbnail);

    // -----------------------------
    // Validation
    // -----------------------------
    /**
     * Validates if an image exists and belongs to the user.
     * 
     * @param imageId The image ID.
     * @param userId  The user ID.
     * @return True if valid, false otherwise.
     */
    boolean validateImage(Long imageId, Long userId);
}
