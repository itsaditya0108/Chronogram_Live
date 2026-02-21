package com.company.image_service.config; // Package for configuration classes

import org.springframework.context.annotation.Bean; // Bean annotation
import org.springframework.context.annotation.Configuration; // Configuration annotation
import org.springframework.context.annotation.Profile; // Profile annotation
import org.springframework.web.servlet.config.annotation.CorsRegistry; // CORS registry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer; // MVC configurer interface

@Configuration // Marks this class as a configuration source
@Profile("!prod") // Active only when 'prod' profile is NOT active (i.e., local/dev)
public class CorsConfig {

    // Bean to configure Cross-Origin Resource Sharing (CORS) settings
    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                // Allow CORS requests to API endpoints
                registry.addMapping("/api/**")
                        .allowedOrigins(
                                "http://localhost:8082", // Local Auth App
                                "http://127.0.0.1:8082", // Local IP
                                "http://192.168.1.5:8082") // Network IP
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS") // Allowed HTTP methods
                        .allowedHeaders("*") // Allow all headers
                        .allowCredentials(true); // Allow sending cookies/credentials
            }
        };
    }
}
