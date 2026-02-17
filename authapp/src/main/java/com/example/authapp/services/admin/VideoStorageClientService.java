package com.example.authapp.services.admin;

import com.example.authapp.dto.admin.UserVideoStorageResponse;
import com.example.authapp.dto.admin.VideoStorageSummaryResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class VideoStorageClientService {

    private final RestTemplate restTemplate;

    @Value("${services.video-service.base-url}")
    private String videoServiceBaseUrl;

    public VideoStorageClientService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public VideoStorageSummaryResponse getVideoStorageSummary() {
        String url = videoServiceBaseUrl + "/internal/storage/summary";
        return restTemplate.getForObject(url, VideoStorageSummaryResponse.class);
    }

    public UserVideoStorageResponse getUserVideoStorage(Long userId) {
        String url = videoServiceBaseUrl + "/internal/storage/user/" + userId;
        try {
            return restTemplate.getForObject(url, UserVideoStorageResponse.class);
        } catch (Exception e) {
            e.printStackTrace();
            return new UserVideoStorageResponse();
        }
    }

}
