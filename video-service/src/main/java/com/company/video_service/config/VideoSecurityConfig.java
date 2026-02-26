package com.company.video_service.config; // Package for configuration classes

import com.company.video_service.security.JwtAuthenticationFilter; // Import custom JWT filter
import com.company.video_service.security.JwtUtil; // Import JWT utility
import org.springframework.beans.factory.annotation.Value; // Value annotation
import org.springframework.context.annotation.Bean; // Bean annotation
import org.springframework.context.annotation.Configuration; // Configuration annotation
import org.springframework.context.annotation.Profile; // Profile annotation
import org.springframework.security.config.annotation.web.builders.HttpSecurity; // HttpSecurity builder
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity; // Enable Web Security annotation
import org.springframework.security.config.http.SessionCreationPolicy; // Session creation policy
import org.springframework.security.web.SecurityFilterChain; // Security filter chain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter; // UsernamePasswordAuthenticationFilter class
import org.springframework.web.cors.CorsConfiguration; // CORS configuration class
import org.springframework.web.cors.CorsConfigurationSource; // CORS source interface
import org.springframework.web.cors.UrlBasedCorsConfigurationSource; // URL-based CORS source implementation

import java.util.List; // List interface

@Configuration // Marks this class as a configuration source for beans
@EnableWebSecurity // Enables Spring Security's web security support
@Profile("prod") // Only active when the "prod" profile is active
public class VideoSecurityConfig { // Security configuration for production environment

    @Value("${auth.service.base-url}") // Inject Auth Service URL from properties
    private String authServiceBaseUrl;

    @Value("${security.jwt.secret}") // Inject JWT secret key from properties
    private String jwtSecret;

    @Bean // Define JwtUtil bean
    public JwtUtil jwtUtil() {
        return new JwtUtil(jwtSecret); // Initialize with secret
    }

    @Bean // Define JwtAuthenticationFilter bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(JwtUtil jwtUtil) {
        return new JwtAuthenticationFilter(jwtUtil, authServiceBaseUrl); // Initialize filter with dependencies
    }

    @Bean // Define CORS configuration source
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // Allow all origins for prod to enable cross-port/cross-host access
        configuration.setAllowedOriginPatterns(List.of("*")); // Allow any origin pattern
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS")); // Allow standard HTTP
                                                                                             // methods
        configuration.setAllowedHeaders(List.of("*")); // Allow all headers
        configuration.setAllowCredentials(true); // Allow credentials (cookies, auth headers)

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration); // Apply CORS config to all paths
        return source;
    }

    @Bean // Define the Security Filter Chain
    public SecurityFilterChain filterChain(
            HttpSecurity http, // HttpSecurity object to configure security settings
            JwtAuthenticationFilter jwtFilter) throws Exception { // Inject custom JWT filter

        http
                .csrf(csrf -> csrf.disable()) // Disable CSRF (using stateless APIs)
                .cors(cors -> cors.configurationSource(corsConfigurationSource())) // Configure CORS
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS)) // Stateless session
                                                                                                    // policy (no
                                                                                                    // server-side
                                                                                                    // sessions)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/**").permitAll() // Allow public access to actuator endpoints
                                                                     // (monitoring)
                        .requestMatchers("/internal/**").permitAll()
                        .requestMatchers("/api/**").authenticated() // Require authentication for API endpoints
                        .anyRequest().permitAll()) // Allow all other requests (adjust as needed)
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class); // Add JWT filter before the
                                                                                         // standard username/password
                                                                                         // filter

        return http.build(); // Build and return the filter chain
    }
}
