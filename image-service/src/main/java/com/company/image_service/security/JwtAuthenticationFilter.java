package com.company.image_service.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final String authServiceUrl;

    public JwtAuthenticationFilter(JwtUtil jwtUtil, String authServiceUrl) {
        this.jwtUtil = jwtUtil;
        this.authServiceUrl = authServiceUrl;
    }

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        // 0️⃣ Allow OPTIONS (CORS preflight) requests
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_OK);
            filterChain.doFilter(request, response);
            return;
        }

        // 1️⃣ Read Authorization header
        String authHeader = request.getHeader("Authorization");

        // 1.1️⃣ Check for token in Query Param (for <img> tags)
        if (authHeader == null && request.getParameter("token") != null) {
            authHeader = "Bearer " + request.getParameter("token");
        }

        String uri = request.getRequestURI();

        // DEV-ONLY: Allow image download without JWT (Fallback)
        if (((uri.startsWith("/api/images/") && uri.endsWith("/download")) ||
             uri.startsWith("/api/profile-picture/"))) {
            // Only skip if no token provided at all
            if (authHeader == null) {
                filterChain.doFilter(request, response);
                return;
            }
        }

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        // 2️⃣ Extract token
        String token = authHeader.substring(7);

        try {
            // 3️⃣ Validate token (signature + expiry)
            Claims claims = jwtUtil.validateAndGetClaims(token);

            // 4️⃣ Check Revocation (New)
            if (!isSessionValid(token)) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }

            // 5️⃣ Extract userId from standard JWT "sub"
            String subject = claims.getSubject(); // sub
            if (subject == null) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }

            Long userId;
            try {
                userId = Long.parseLong(subject);
            } catch (NumberFormatException e) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }

            // 6️⃣ Attach userId to request for downstream layers
            request.setAttribute("userId", userId);

            // 6.5️⃣ Populate Spring Security Context (CRITICAL for Prod Profile)
            org.springframework.security.authentication.UsernamePasswordAuthenticationToken auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                    userId, null, java.util.Collections.emptyList());
            org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);

            // 7️⃣ Continue request
            filterChain.doFilter(request, response);

        } catch (Exception ex) {
            // Any JWT validation failure
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        }
    }

    private static final java.util.Map<String, Long> VALID_SESSION_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 30000; // 30 seconds

    private boolean isSessionValid(String token) {
        long now = System.currentTimeMillis();
        if (VALID_SESSION_CACHE.containsKey(token)) {
            if (now - VALID_SESSION_CACHE.get(token) < CACHE_TTL_MS) {
                return true;
            }
        }

        try {
            logger.info("[HANDSHAKE] Requesting session validation from Auth Service...");
            java.net.URL url = new java.net.URL(authServiceUrl + "/api/auth/validate-session");
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bearer " + token);
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);

            int code = conn.getResponseCode();
            if (code == 200) {
                logger.info("[HANDSHAKE] Session validated successfully.");
                VALID_SESSION_CACHE.put(token, now);
                return true;
            }
            logger.warn("[HANDSHAKE] Session validation failed. Service responded with: {}", code);
            return false;
        } catch (Exception e) {
            logger.error("[HANDSHAKE-ERROR] Auth Service unreachable: {}", e.getMessage());
            return false;
        }
    }
}
