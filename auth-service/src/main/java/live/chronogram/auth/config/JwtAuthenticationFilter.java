package live.chronogram.auth.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Cookie;
import live.chronogram.auth.security.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Filter that intercepts every request to check for a valid JWT in the
 * Authorization header.
 * If a valid JWT is found, it sets the authentication in the SecurityContext.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    /**
     * Filters incoming requests to authenticate users based on JWT.
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        try {
            // Extract JWT from the Authorization header
            String jwt = getJwtFromRequest(request);

            // Validate the token and ensure it's not null
            if (jwt != null && jwtTokenProvider.validateToken(jwt)) {
                // Extract user identity and role from the token
                Long userId = jwtTokenProvider.getUserIdFromToken(jwt);
                String role = jwtTokenProvider.getRoleFromToken(jwt);

                // Create authorities based on the user's role
                List<SimpleGrantedAuthority> authorities = (role != null
                        && !role.isEmpty()) ? java.util.Collections.singletonList(
                                new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + role))
                                : java.util.Collections.emptyList();

                // Create an authentication object and put it in the security context
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        String.valueOf(userId), null, authorities);

                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            logger.error("JWT Token is expired: " + e.getMessage());
        } catch (io.jsonwebtoken.UnsupportedJwtException e) {
            logger.error("JWT Token is unsupported: " + e.getMessage());
        } catch (io.jsonwebtoken.MalformedJwtException e) {
            logger.error("JWT Token is malformed: " + e.getMessage());
        } catch (io.jsonwebtoken.security.SignatureException e) {
            logger.error("JWT Signature is invalid: " + e.getMessage());
        } catch (Exception e) {
            // Log other unexpected errors
            logger.error("Could not set user authentication in security context: " + e.getMessage());
        }

        // Continue with the next filter in the chain
        filterChain.doFilter(request, response);
    }

    /**
     * Extracts the JWT token from the Authorization header.
     * 
     * @param request The HttpServletRequest.
     * @return The JWT token string, or null if not found.
     */
    private String getJwtFromRequest(HttpServletRequest request) {
        // 1. Check Authorization Header
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }

        // 2. Check Admin Cookie (for production security/page navigation)
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("adminToken".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}
