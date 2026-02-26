package com.example.authapp.services.admin;

import com.example.authapp.dto.admin.ImageStorageSummaryResponse;
import com.example.authapp.dto.admin.UserImageStorageResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class ImageStorageClientService {

    private final RestTemplate restTemplate;

    @Value("${services.image-service.base-url}")
    private String imageServiceBaseUrl;

    public ImageStorageClientService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public ImageStorageSummaryResponse getImageStorageSummary() {
        String url = imageServiceBaseUrl + "/internal/storage/summary";
        return restTemplate.getForObject(url, ImageStorageSummaryResponse.class);
    }

    public UserImageStorageResponse getUserImageStorage(Long userId) {
        String url = imageServiceBaseUrl + "/internal/storage/user/" + userId;
        try {
            return restTemplate.getForObject(url, UserImageStorageResponse.class);
        } catch (Exception e) {
            e.printStackTrace();
            return new UserImageStorageResponse(); // Return empty object on error
        }
    }

}
