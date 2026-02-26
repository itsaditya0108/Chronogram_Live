package com.example.authapp.services.admin;

import com.example.authapp.dto.admin.*;
import com.example.authapp.entity.User;
import com.example.authapp.entity.UserStatus;
import com.example.authapp.repository.UserRepository;
import com.example.authapp.repository.UserSessionRepository;
import com.example.authapp.repository.UserStatusRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminUserService {

        private final UserRepository userRepository;
        private final UserStatusRepository userStatusRepository;
        private final UserSessionRepository userSessionRepository;
        private final ImageStorageClientService imageStorageClientService;
        private final VideoStorageClientService videoStorageClientService;

        public AdminUserService(UserRepository userRepository, UserStatusRepository userStatusRepository,
                        UserSessionRepository userSessionRepository,
                        ImageStorageClientService imageStorageClientService,
                        VideoStorageClientService videoStorageClientService) {
                this.userRepository = userRepository;
                this.userStatusRepository = userStatusRepository;
                this.userSessionRepository = userSessionRepository;
                this.imageStorageClientService = imageStorageClientService;
                this.videoStorageClientService = videoStorageClientService;
        }

        public Page<AdminUserListResponse> getUsers(int page, int size) {

                Page<User> users = userRepository.findAll(PageRequest.of(page, size));

                return users.map(user -> {
                        UserImageStorageResponse imageStorage = imageStorageClientService
                                        .getUserImageStorage(user.getId());
                        UserVideoStorageResponse videoStorage = videoStorageClientService
                                        .getUserVideoStorage(user.getId());

                        return new AdminUserListResponse(
                                        user.getId(),
                                        user.getName(),
                                        user.getEmail(),
                                        user.getPhone(),
                                        user.getStatus().getName(),
                                        user.isEmailVerified(),
                                        user.isPhoneVerified(),
                                        user.getCreatedTimestamp(),
                                        imageStorage.getTotalFiles(),
                                        imageStorage.getTotalBytes(),
                                        videoStorage.getTotalFiles(),
                                        videoStorage.getTotalBytes());
                });
        }

        public AdminUserDetailsResponse getUserDetails(Long userId) {

                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new RuntimeException("User not found"));

                return new AdminUserDetailsResponse(
                                user.getId(),
                                user.getName(),
                                user.getEmail(),
                                user.getPhone(),
                                user.isEmailVerified(),
                                user.isPhoneVerified(),
                                user.getStatus().getName(),
                                user.getFailedLoginAttempts(),
                                user.getLockedUntil(),
                                user.getCreatedTimestamp(),
                                user.getUpdatedAt());
        }

        @Transactional
        public void blockUser(Long userId, String reason) {

                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new RuntimeException("User not found"));

                UserStatus blockedStatus = userStatusRepository.findById("03")
                                .orElseThrow(() -> new RuntimeException("Blocked status not found"));

                user.setStatus(blockedStatus);
                user.setStatusReason(reason); // Set reason
                userRepository.save(user);

                userSessionRepository.revokeAllSessions(userId);
        }

        @Transactional
        public void inactiveUser(Long userId) {

                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new RuntimeException("User not found"));

                UserStatus inactiveStatus = userStatusRepository.findById("02")
                                .orElseThrow(() -> new RuntimeException("Inactive status not found"));

                user.setStatus(inactiveStatus);
                userRepository.save(user);

                userSessionRepository.revokeAllSessions(userId);
        }

        @Transactional
        public void unblockUser(Long userId) {

                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new RuntimeException("User not found"));

                UserStatus activeStatus = userStatusRepository.findById("01")
                                .orElseThrow(() -> new RuntimeException("Active status not found"));

                user.setStatus(activeStatus);
                userRepository.save(user);
        }

        public Page<AdminUserListResponse> searchUsers(
                        String query,
                        String statusId,
                        int page,
                        int size,
                        String sortBy,
                        String direction) {

                Sort sort = direction.equalsIgnoreCase("asc")
                                ? Sort.by(sortBy).ascending()
                                : Sort.by(sortBy).descending();

                Pageable pageable = PageRequest.of(page, size, sort);

                Page<User> users = userRepository.searchUsers(query, statusId, pageable);

                return users.map(user -> {
                        UserImageStorageResponse imageStorage = imageStorageClientService
                                        .getUserImageStorage(user.getId());
                        UserVideoStorageResponse videoStorage = videoStorageClientService
                                        .getUserVideoStorage(user.getId());

                        if (imageStorage == null)
                                imageStorage = new UserImageStorageResponse();
                        if (videoStorage == null)
                                videoStorage = new UserVideoStorageResponse();

                        return new AdminUserListResponse(
                                        user.getId(),
                                        user.getName(),
                                        user.getEmail(),
                                        user.getPhone(),
                                        user.getStatus().getName(),
                                        user.isEmailVerified(),
                                        user.isPhoneVerified(),
                                        user.getCreatedTimestamp(),
                                        imageStorage.getTotalFiles(),
                                        imageStorage.getTotalBytes(),
                                        videoStorage.getTotalFiles(),
                                        videoStorage.getTotalBytes());
                });
        }

        public AdminUserFullDetailsResponse getFullDetails(Long userId) {

                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new RuntimeException("User not found"));

                UserImageStorageResponse imageStorage = imageStorageClientService.getUserImageStorage(userId);

                UserVideoStorageResponse videoStorage = videoStorageClientService.getUserVideoStorage(userId);

                return new AdminUserFullDetailsResponse(
                                user.getId(),
                                user.getName(),
                                user.getEmail(),
                                user.getPhone(),
                                user.getStatus().getName(),
                                user.isEmailVerified(),
                                user.isPhoneVerified(),
                                user.getCreatedTimestamp(),
                                imageStorage.getTotalFiles(),
                                imageStorage.getTotalBytes(),
                                videoStorage.getTotalFiles(),
                                videoStorage.getTotalBytes());
        }

}
