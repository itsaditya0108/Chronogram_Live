package com.company.image_service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class StaticResourceConfig implements WebMvcConfigurer {

    @Value("${image.storage.base-path:D:/data/image-service}")
    private String storageBasePath;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Expose the unencrypted profile pictures stored in users/{userId}/profile/...
        String location = "file:" + storageBasePath + "/users/";
        registry.addResourceHandler("/api/users/**")
                .addResourceLocations(location);
    }
}
