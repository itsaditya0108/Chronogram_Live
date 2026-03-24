package live.chronogram.auth.service;

import live.chronogram.auth.dto.ProfileDto;
import live.chronogram.auth.dto.UpdateProfileRequest;
import live.chronogram.auth.model.User;
import live.chronogram.auth.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing user profile information and photo uploads.
 * Communicates with the external image-service for media storage.
 */
@Service
public class ProfileService {

    @Autowired
    private UserRepository userRepository;


    /**
     * Retrieves the basic profile summary for a user.
     * @param userId The unique user ID.
     * @return A ProfileDto containing name, email, and photo URL.
     */
    public ProfileDto getProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (Boolean.TRUE.equals(user.getIsDeleted())) {
            throw new live.chronogram.auth.exception.AuthException(org.springframework.http.HttpStatus.GONE,
                    "Account deleted.");
        }

        return new ProfileDto(user.getName(), user.getEmail());
    }

    /**
     * Updates the user's display name.
     * @param userId The unique user ID.
     * @param request Contains the new name.
     * @return The updated ProfileDto.
     */
    @Transactional
    public ProfileDto updateProfile(Long userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (Boolean.TRUE.equals(user.getIsDeleted())) {
            throw new live.chronogram.auth.exception.AuthException(org.springframework.http.HttpStatus.GONE,
                    "Account deleted.");
        }

        user.setName(request.getName());
        userRepository.save(user);

        return new ProfileDto(user.getName(), user.getEmail());
    }


}
