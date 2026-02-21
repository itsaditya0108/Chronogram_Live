package com.company.image_service.service; // Package for service interfaces

import com.company.image_service.entity.ProfilePicture; // ProfilePicture entity
import org.springframework.core.io.Resource; // Resource interface for file downloads
import org.springframework.web.multipart.MultipartFile; // Multipart file support

public interface ProfilePictureService {

    /**
     * Uploads a new profile picture for a user.
     * 
     * @param userId The ID of the user.
     * @param file   The image file.
     * @return The created ProfilePicture entity.
     */
    ProfilePicture upload(Long userId, MultipartFile file);

    /**
     * Sets a specific profile picture from history as active.
     * 
     * @param userId           The user ID.
     * @param profilePictureId The ID of the profile picture to activate.
     */
    void setProfilePicture(Long userId, Long profilePictureId);

    /**
     * Retrieves the history of all profile pictures uploaded by the user.
     * 
     * @param userId The user ID.
     * @return A list of ProfilePicture entities.
     */
    java.util.List<ProfilePicture> getHistory(Long userId);

    /**
     * Retrieves the resource for the user's active small profile picture.
     * 
     * @param userId The user ID.
     * @return The file resource for the small image.
     */
    Resource getSmall(Long userId);

    /**
     * Retrieves the resource for the user's active medium profile picture.
     * 
     * @param userId The user ID.
     * @return The file resource for the medium image.
     */
    Resource getMedium(Long userId);

    /**
     * Retrieves the resource for the user's active original profile picture.
     * 
     * @param userId The user ID.
     * @return The file resource for the original image.
     */
    Resource getOriginal(Long userId);

    /**
     * Deletes (or deactivates) the user's current profile picture.
     * 
     * @param userId The user ID.
     */
    void delete(Long userId);

    /**
     * Generic method to retrieve a specific profile picture resource by ID and
     * type.
     * 
     * @param userId           The user ID (for validation).
     * @param profilePictureId The profile picture ID.
     * @param type             The type (size) of the image variant (e.g., 'small',
     *                         'medium', 'original').
     * @return The file resource.
     */
    // Generic retrieval by ID
    Resource getProfilePictureResource(Long userId, Long profilePictureId, String type);
}
