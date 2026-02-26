package com.company.video_service.config; // Package for configuration classes

import org.springframework.context.annotation.Bean; // Bean annotation
import org.springframework.context.annotation.Configuration; // Configuration annotation
import org.springframework.context.annotation.Profile; // Profile annotation
import org.springframework.security.config.annotation.web.builders.HttpSecurity; // HttpSecurity builder
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity; // Enable Web Security annotation
import org.springframework.security.web.SecurityFilterChain; // Security filter chain
import org.springframework.web.cors.CorsConfiguration; // CORS configuration class
import org.springframework.web.cors.CorsConfigurationSource; // CORS source interface
import org.springframework.web.cors.UrlBasedCorsConfigurationSource; // URL-based CORS source implementation

import java.util.List; // List interface

@Configuration // Marks this class as a configuration source for beans
@EnableWebSecurity // Enables Spring Security's web security support
@Profile("!prod") // Active for any profile EXCEPT "prod" (e.g., dev, test)
public class LocalSecurityConfig { // Security configuration for local/development environments

    @Bean // Define the Security Filter Chain for local/dev
    public SecurityFilterChain localFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // Disable CSRF for easier testing
                .cors(cors -> cors.configurationSource(corsConfigurationSource())) // Enable CORS with local settings
                .authorizeHttpRequests(auth -> auth
                                .requestMatchers("/internal/**").permitAll()
                                .anyRequest().permitAll() // Allow ALL requests without authentication for development
                                                  // convenience
                // Note: In real scenarios, you might still want auth, but this setup implies
                // "open" access for dev
                );

        return http.build(); // Build and return the filter chain
    }

    @Bean // Define CORS configuration source for local/dev
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("*")); // Allow ALL origins for local development
        configuration.setAllowedMethods(List.of("*")); // Allow ALL HTTP methods
        configuration.setAllowedHeaders(List.of("*")); // Allow ALL headers
        configuration.setAllowCredentials(true); // Allow credentials

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration); // Apply to all paths
        return source;
    }
}
