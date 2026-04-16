package com.company.video_service.config;

import com.company.video_service.security.JwtAuthenticationFilter;
import com.company.video_service.security.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@Order(Ordered.HIGHEST_PRECEDENCE) // Ensure this has top priority in the Spring environment
public class VideoSecurityConfig {

    @Value("${auth.service.base-url:http://localhost:8081}")
    private String authServiceBaseUrl;

    @Value("${security.jwt.secret}")
    private String jwtSecret;

    @Bean
    public JwtUtil jwtUtil() {
        return new JwtUtil(jwtSecret);
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(JwtUtil jwtUtil) {
        return new JwtAuthenticationFilter(jwtUtil, authServiceBaseUrl);
    }

    @Bean
    public UserDetailsService userDetailsService() {
        // Mute the 'Generated Security Password' warning
        return new InMemoryUserDetailsManager(
                User.withUsername("internal")
                    .password("{noop}internal")
                    .roles("INTERNAL")
                    .build());
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter) throws Exception {
        http
                // Reclaim the API territory exclusively to prevent conflict with default filter chains
                .securityMatcher(new AntPathRequestMatcher("/api/**"))
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().authenticated())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
