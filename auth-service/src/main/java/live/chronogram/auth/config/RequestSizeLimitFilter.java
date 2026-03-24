package live.chronogram.auth.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filter responsible for enforcing request payload size limits.
 * Protects the service from Denial-of-Service (DoS) attacks via oversized payloads.
 * Implements tiered limits for different types of requests (Multipart vs JSON).
 */
@Component
@Order(1) // Ensures it runs early in the filter chain to reject large requests immediately
public class RequestSizeLimitFilter extends OncePerRequestFilter {

    // Maximum 15MB for general multipart (images/files)
    private static final long MAX_OVERALL_SIZE = 15 * 1024 * 1024;
    // Maximum 5MB specifically for profile photos
    private static final long MAX_PROFILE_PHOTO_SIZE = 5 * 1024 * 1024;
    // Maximum 100KB for standard JSON requests (auth info, names, etc.)
    private static final long MAX_JSON_SIZE = 100 * 1024; 

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        long contentLength = request.getContentLengthLong();

        // 1. Overall safety limit
        if (contentLength > MAX_OVERALL_SIZE) {
            response.setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
            response.getWriter().write("{\"message\": \"Request payload too large. Maximum allowed size is 15MB.\"}");
            response.setContentType("application/json");
            return;
        }

        // 2. Specific limit for Profile Photo (POST /api/profile/photo)
        if (request.getRequestURI().endsWith("/api/profile/photo") && "POST".equalsIgnoreCase(request.getMethod())) {
            if (contentLength > MAX_PROFILE_PHOTO_SIZE) {
                response.setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
                response.getWriter().write("{\"message\": \"Profile photo too large. Maximum allowed size is 5MB.\"}");
                response.setContentType("application/json");
                return;
            }
        }

        // 2. Strict JSON limit (blocks huge text payloads in typical REST calls)
        String contentType = request.getContentType();
        if (contentType != null && contentType.toLowerCase().contains("application/json")) {
            if (contentLength > MAX_JSON_SIZE) { 
                response.setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
                response.getWriter().write("{\"message\": \"JSON payload too large. Maximum allowed size is 100KB.\"}");
                response.setContentType("application/json");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
