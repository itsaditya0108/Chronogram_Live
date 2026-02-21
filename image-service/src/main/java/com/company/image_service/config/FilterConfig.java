package com.company.image_service.config; // Package for configuration classes

import com.company.image_service.security.JwtAuthenticationFilter; // JWT Authentication Filter
import com.company.image_service.security.JwtUtil; // JWT Utility class
import org.springframework.beans.factory.annotation.Value; // Annotation for injecting property values
import org.springframework.boot.web.servlet.FilterRegistrationBean; // Bean for registering filters
import org.springframework.context.annotation.Bean; // Bean annotation
import org.springframework.context.annotation.Configuration; // Configuration annotation
import org.springframework.context.annotation.Profile; // Profile annotation

@Configuration // Marks this class as a configuration source
@Profile("!prod") // Active only when 'prod' profile is NOT active (i.e., local/dev)
public class FilterConfig {

    @Value("${security.jwt.secret}") // Inject JWT secret from application.properties
    private String jwtSecret;

    @Value("${auth.service.base-url}") // Inject Auth Service Base URL from application.properties
    private String authServiceBaseUrl;

    // Registers the JwtAuthenticationFilter manually for non-prod environments
    // In prod, SecurityConfig likely handles this via the Security Filter Chain
    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtFilter() {

        // Initialize JwtUtil and the Filter with injected values
        JwtUtil jwtUtil = new JwtUtil(jwtSecret);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtUtil, authServiceBaseUrl);

        // Create a registration bean for the filter
        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>();

        registration.setFilter(filter); // Set the filter instance
        registration.addUrlPatterns("/api/*"); // Apply filter to all API endpoints
        registration.setOrder(1); // Set the execution order (1 = high priority)

        return registration;
    }
}
