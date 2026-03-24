package live.chronogram.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Security configuration for the Authentication Service.
 * Configures HTTP security, CORS, and password encoding.
 */
@Configuration
@EnableWebSecurity
@org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Autowired
    private CorrelationIdFilter correlationIdFilter;

    /**
     * Configures the security filter chain.
     * 
     * @param http The HttpSecurity object to configure.
     * @return The configured SecurityFilterChain.
     * @throws Exception If an error occurs during configuration.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 1. Stateless API: Disable CSRF (Cross-Site Request Forgery) as we use JWTs, not cookies
                .csrf(AbstractHttpConfigurer::disable)
                
                // 2. Global CORS Policy: See corsConfigurationSource()
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                
                // 3. Stateless Session: No server-side storage of user sessions (improves scalability)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                
                // 4. Authorization Matrix: Define what is public vs protected
                .authorizeHttpRequests(auth -> auth
                        // Protected Routes (Require valid JWT)
                        .requestMatchers("/api/auth/me").authenticated()
                        .requestMatchers("/api/profile/**").authenticated()
                        .requestMatchers("/api/storage/**").authenticated()
                        .requestMatchers("/api/settings/**").authenticated()
                        .requestMatchers("/api/account/**").authenticated()
                        
                        // Public Routes (Entry points for registration and info)
                        .requestMatchers("/api/app/**").permitAll() 
                        .requestMatchers("/api/auth/**").permitAll() 
                        
                        // Development Safety: Allow other requests or unmapped paths to avoid 401s during testing
                        .anyRequest().permitAll()) 
                        
                // 5. Traceability: Set Correlation ID before any other processing
                .addFilterBefore(correlationIdFilter, UsernamePasswordAuthenticationFilter.class)

                // 6. JWT Authentication Filter: Intercept every request before standard auth to validate 'Authorization' header
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Configures CORS (Cross-Origin Resource Sharing) settings.
     * 
     * @return The CorsConfigurationSource bean.
     */
    @Bean
    public org.springframework.web.cors.CorsConfigurationSource corsConfigurationSource() {
        org.springframework.web.cors.CorsConfiguration configuration = new org.springframework.web.cors.CorsConfiguration();
        
        // Allowed Origins: Permit all origins for development (Postman, Flutter Web, localhost)
        configuration.setAllowedOrigins(java.util.List.of("*"));
        
        // Allowed Methods: standard REST verbs
        configuration.setAllowedMethods(java.util.List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        
        // Allowed Headers: permit all (required for 'Authorization' and 'Content-Type' headers)
        configuration.setAllowedHeaders(java.util.List.of("*"));
        
        org.springframework.web.cors.UrlBasedCorsConfigurationSource source = new org.springframework.web.cors.UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * Bean for password hashing using BCrypt.
     * 
     * @return The PasswordEncoder bean.
     */
    @Bean
    public org.springframework.security.crypto.password.PasswordEncoder passwordEncoder() {
        // Industry standard hashing algorithm with salt automatically handled
        return new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
    }
}
