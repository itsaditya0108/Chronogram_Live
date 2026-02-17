package com.example.authapp.services.admin;

import com.example.authapp.dto.admin.AdminFullDashboardResponse;
import com.example.authapp.dto.admin.ImageStorageSummaryResponse;
import com.example.authapp.dto.admin.VideoStorageSummaryResponse;
import com.example.authapp.repository.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class AdminFullDashboardService {

    private final UserRepository userRepository;
    private final ImageStorageClientService imageStorageClientService;
    private final VideoStorageClientService videoStorageClientService;

    public AdminFullDashboardService(
            UserRepository userRepository,
            ImageStorageClientService imageStorageClientService,
            VideoStorageClientService videoStorageClientService
    ) {
        this.userRepository = userRepository;
        this.imageStorageClientService = imageStorageClientService;
        this.videoStorageClientService = videoStorageClientService;
    }

    public AdminFullDashboardResponse getFullDashboard() {

        long totalUsers = userRepository.count();
        long activeUsers = userRepository.countByStatus_Id("01");
        long inactiveUsers = userRepository.countByStatus_Id("02");
        long blockedUsers = userRepository.countByStatus_Id("03");
        long deletedUsers = userRepository.countByStatus_Id("04");

        ImageStorageSummaryResponse imageSummary = imageStorageClientService.getImageStorageSummary();
        VideoStorageSummaryResponse videoSummary = videoStorageClientService.getVideoStorageSummary();

        return new AdminFullDashboardResponse(
                totalUsers,
                activeUsers,
                inactiveUsers,
                blockedUsers,
                deletedUsers,
                imageSummary.getTotalFiles(),
                imageSummary.getTotalBytes(),
                videoSummary.getTotalFiles(),
                videoSummary.getTotalBytes()
        );
    }
}
