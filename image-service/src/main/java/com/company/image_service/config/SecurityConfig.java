package com.company.image_service.config; // Package for configuration classes

import com.company.image_service.security.JwtAuthenticationFilter; // JWT Authentication Filter
import com.company.image_service.security.JwtUtil; // JWT Utility
import org.springframework.beans.factory.annotation.Value; // Value annotation for property injection
import org.springframework.context.annotation.Bean; // Bean annotation
import org.springframework.context.annotation.Configuration; // Configuration annotation
import org.springframework.context.annotation.Profile; // Profile annotation
import org.springframework.security.config.annotation.web.builders.HttpSecurity; // HTTP Security builder
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity; // Enable Web Security annotation
import org.springframework.security.config.http.SessionCreationPolicy; // Session Creation Policy enum
import org.springframework.security.web.SecurityFilterChain; // Security Filter Chain interface
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter; // Username Password Authentication Filter class
import org.springframework.web.cors.CorsConfiguration; // CORS Configuration class
import org.springframework.web.cors.CorsConfigurationSource; // CORS Configuration Source interface
import org.springframework.web.cors.UrlBasedCorsConfigurationSource; // URL-based CORS source implementation

import java.util.List; // List utility

@Configuration // Marks this class as a configuration source
@EnableWebSecurity // Enables Spring Security's web security support
@Profile("prod") // Active only when 'prod' profile is active
public class SecurityConfig {

        @Value("${auth.service.base-url}") // Inject Auth Service Base URL from properties
        private String authServiceBaseUrl;

        @Value("${security.jwt.secret}") // Inject JWT secret from properties
        private String jwtSecret;

        // Bean definition for JwtUtil, initialized with the secret key
        @Bean
        public JwtUtil jwtUtil() {
                return new JwtUtil(jwtSecret);
        }

        // Bean definition for JwtAuthenticationFilter, using JwtUtil and Auth Service
        // URL
        @Bean
        public JwtAuthenticationFilter jwtAuthenticationFilter(JwtUtil jwtUtil) {
                return new JwtAuthenticationFilter(jwtUtil, authServiceBaseUrl);
        }

        // Configures CORS settings for the production environment
        @Bean
        public CorsConfigurationSource corsConfigurationSource() {
                CorsConfiguration configuration = new CorsConfiguration();
                // Allow all origins for prod to enable cross-port/cross-host access
                // specific origins should be restricted in a strict production environment
                configuration.setAllowedOriginPatterns(List.of("*"));
                configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS")); // Allowed methods
                configuration.setAllowedHeaders(List.of("*")); // Allow all headers
                configuration.setAllowCredentials(true); // Allow credentials

                UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
                source.registerCorsConfiguration("/**", configuration); // Apply to all paths
                return source;
        }

        // Configures the security filter chain for production
        @Bean
        public SecurityFilterChain filterChain(
                        HttpSecurity http,
                        JwtAuthenticationFilter jwtFilter) throws Exception {

                http
                                .csrf(csrf -> csrf.disable()) // Disable CSRF as we use tokens
                                .cors(cors -> cors.configurationSource(corsConfigurationSource())) // Enable CORS
                                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS)) // Stateless
                                                                                                                    // session
                                                                                                                    // (no
                                                                                                                    // JSESSIONID)
                                .authorizeHttpRequests(auth -> auth
                                        .requestMatchers("/internal/**").permitAll()
                                        .requestMatchers("/actuator/**").permitAll() // Allow health checks
                                        .requestMatchers("/api/**").authenticated() // Require authentication
                                                                                            // for API endpoints
                                                .anyRequest().permitAll()) // Permit other requests (fallback)
                                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class); // Add JWT
                                                                                                         // filter
                                                                                                         // before
                                                                                                         // standard
                                                                                                         // auth filter
                return http.build(); // Build the chain
        }
}