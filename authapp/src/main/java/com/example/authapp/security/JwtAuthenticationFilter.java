package com.example.authapp.security;

import com.example.authapp.entity.User;
import com.example.authapp.repository.UserRepository;
import com.example.authapp.services.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final com.example.authapp.repository.UserSessionRepository userSessionRepository;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            UserRepository userRepository,
            com.example.authapp.repository.UserSessionRepository userSessionRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.userSessionRepository = userSessionRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws java.io.IOException, jakarta.servlet.ServletException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();

        // IMPORTANT: skip admin routes
        if (path.startsWith("/api/admin/")) {
            filterChain.doFilter(request, response);
            return;
        }


        String token = authHeader.substring(7);

        try {
            Long userId = jwtService.validateAndGetUserId(token);
            Long sessionId = jwtService.getSessionId(token);

            // Check if session is revoked
            if (sessionId != null) {
                boolean isRevoked = userSessionRepository.findById(sessionId)
                        .map(com.example.authapp.entity.UserSession::isRevoked)
                        .orElse(true); // If session not found, treat as revoked

                if (isRevoked) {
                    logger.debug("Session is revoked or not found for sessionId: {}", sessionId);
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    return;
                }
            }

            User user = userRepository.findById(userId)
                    .orElseThrow();

            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    user,
                    null,
                    java.util.Collections.emptyList());

            SecurityContextHolder.getContext()
                    .setAuthentication(authentication);

            String statusId = user.getStatus().getId();

            if ("02".equals(statusId) || "03".equals(statusId) || "04".equals(statusId)) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }

        } catch (Exception e) {
            logger.error("JWT Authentication failed: {}", e.getMessage());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        filterChain.doFilter(request, response);
    }
}
