package com.company.image_service.config; // Package for configuration classes

import org.springframework.context.annotation.Bean; // Bean annotation
import org.springframework.context.annotation.Configuration; // Configuration annotation
import org.springframework.context.annotation.Profile; // Profile annotation
import org.springframework.security.config.annotation.web.builders.HttpSecurity; // HTTP Security builder
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity; // Enable Web Security annotation
import org.springframework.security.web.SecurityFilterChain; // Security Filter Chain interface
import org.springframework.web.cors.CorsConfiguration; // CORS Configuration class
import org.springframework.web.cors.CorsConfigurationSource; // CORS Configuration Source interface
import org.springframework.web.cors.UrlBasedCorsConfigurationSource; // URL-based CORS source implementation

import java.util.List; // List utility

@Configuration // Marks this class as a configuration source
@EnableWebSecurity // Enables Spring Security's web security support
@Profile("!prod") // Active only when 'prod' profile is NOT active (i.e., local/dev)
public class LocalSecurityConfig {

    // Configures the security filter chain for local development
    @Bean
    public SecurityFilterChain localFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                // Disable CSRF protection for easier local testing
                .cors(cors -> cors.configurationSource(corsConfigurationSource())) // Enable CORS with custom source
                .authorizeHttpRequests(auth -> auth
                        // Permit all requests locally. Authentication is handled by
                        // FilterConfig/JwtFilter
                        // This allows looser security controls for development purposes
                        .anyRequest().permitAll());

        return http.build(); // Build the security filter chain
    }

    // Defines the CORS configuration source
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // Allow ALL origins for local development to avoid CORS issues
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(List.of("*")); // Allow all HTTP methods
        configuration.setAllowedHeaders(List.of("*")); // Allow all headers
        configuration.setAllowCredentials(true); // Allow credentials (cookies, auth headers)

        // Register the configuration for all paths (/**)
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
