package com.example.authapp.config;

import com.example.authapp.repository.AdminRepository;
import com.example.authapp.repository.UserRepository;
import com.example.authapp.security.AdminJwtAuthenticationFilter;
import com.example.authapp.security.JwtAuthenticationFilter;
import com.example.authapp.services.JwtService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import com.example.authapp.security.JwtAuthEntryPoint;
import org.springframework.web.client.RestTemplate;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http,
            JwtService jwtService,
            UserRepository userRepository,
            com.example.authapp.repository.UserSessionRepository userSessionRepository,
            AdminRepository adminRepository,
            JwtAuthEntryPoint entryPoint
    ) throws Exception {

        JwtAuthenticationFilter jwtFilter =
                new JwtAuthenticationFilter(jwtService, userRepository, userSessionRepository);

        AdminJwtAuthenticationFilter adminJwtFilter =
                new AdminJwtAuthenticationFilter(jwtService, adminRepository);

        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(request -> {
                    org.springframework.web.cors.CorsConfiguration config = new org.springframework.web.cors.CorsConfiguration();
                    config.setAllowedOriginPatterns(java.util.List.of("*"));
                    config.setAllowedMethods(java.util.List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
                    config.setAllowedHeaders(java.util.List.of("*"));
                    config.setAllowCredentials(true);
                    return config;
                }))
                .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(auth -> auth

                        // ADMIN ROUTES
                        .requestMatchers("/api/admin/auth/**").permitAll()
                        .requestMatchers("/api/admin/**").authenticated()

                        // USER ROUTES
                        .requestMatchers("/api/auth/validate-session").authenticated()
                        .requestMatchers("/api/auth/**").permitAll()

                        // INTERNAL
                        .requestMatchers("/internal/users/by-ids").permitAll()

                        // STATIC
                        .requestMatchers("/", "/*.html", "/login", "/dashboard", "/css/**",
                                "/js/**", "/images/**",
                                "/favicon.ico", "/error").permitAll()

                        // Pass-through
                        .requestMatchers("/api/images/**").permitAll()

                        // Actuator
                        .requestMatchers("/actuator/**").permitAll()

                        .anyRequest().authenticated()
                )

                // IMPORTANT: admin filter should run first
                .addFilterBefore(adminJwtFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

}
