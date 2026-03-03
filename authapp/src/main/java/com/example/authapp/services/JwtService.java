package com.example.authapp.services;

import com.example.authapp.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
public class JwtService {

        @Value("${jwt.secret}")
        private String secret;

        @Value("${jwt.expiration-min:60}")
        private long accessTokenTtlMin;

        public String generateAccessToken(User user, Long sessionId) {
                return Jwts.builder()
                                .setSubject(String.valueOf(user.getId()))
                                .claim("email", user.getEmail())
                                .claim("status", user.getStatus().getName())
                                .claim("sid", sessionId)
                                .setIssuedAt(new Date())
                                .setExpiration(
                                                new Date(System.currentTimeMillis()
                                                                + accessTokenTtlMin * 60 * 1000))
                                .signWith(
                                                Keys.hmacShaKeyFor(secret.getBytes()),
                                                SignatureAlgorithm.HS256)
                                .compact();
        }

        // Strict Base64Url pattern for JWT parts (no padding "=" allowed)
        private static final java.util.regex.Pattern JWT_STRICT_PATTERN = java.util.regex.Pattern.compile(
                        "^[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+$");

        private boolean isStrictlyFormattedJwt(String token) {
                if (token == null || token.isEmpty()) {
                        return false;
                }
                return JWT_STRICT_PATTERN.matcher(token).matches();
        }

        public Long validateAndGetUserId(String token) {
                if (!isStrictlyFormattedJwt(token)) {
                        throw new RuntimeException("Malformed JWT structure");
                }

                Claims claims = Jwts.parser()
                                .verifyWith((javax.crypto.SecretKey) Keys.hmacShaKeyFor(secret.getBytes()))
                                .build()
                                .parseSignedClaims(token)
                                .getPayload();

                return Long.parseLong(claims.getSubject());
        }

        public Long getSessionId(String token) {
                if (!isStrictlyFormattedJwt(token)) {
                        throw new RuntimeException("Malformed JWT structure");
                }

                Claims claims = Jwts.parser()
                                .verifyWith((javax.crypto.SecretKey) Keys.hmacShaKeyFor(secret.getBytes()))
                                .build()
                                .parseSignedClaims(token)
                                .getPayload();

                return claims.get("sid", Long.class);
        }

        public String generateAdminToken(Long adminId, String role) {

                return Jwts.builder()
                                .setSubject(String.valueOf(adminId))
                                .claim("type", "ADMIN")
                                .claim("role", role)
                                .setIssuedAt(new Date())
                                .setExpiration(new Date(System.currentTimeMillis() + 1000L * 60 * 60 * 12))
                                .signWith(Keys.hmacShaKeyFor(secret.getBytes()), SignatureAlgorithm.HS256)
                                .compact();
        }

        public Long validateAndGetAdminId(String token) {
                if (!isStrictlyFormattedJwt(token)) {
                        throw new RuntimeException("Malformed JWT structure");
                }

                Claims claims = Jwts.parser()
                                .verifyWith((javax.crypto.SecretKey) Keys.hmacShaKeyFor(secret.getBytes()))
                                .build()
                                .parseSignedClaims(token)
                                .getPayload();

                String type = claims.get("type", String.class);

                if (!"ADMIN".equals(type)) {
                        throw new RuntimeException("Invalid admin token");
                }

                return Long.parseLong(claims.getSubject());
        }

        // This throws exception automatically if:
        // token expired
        // token invalid
        // signature tampered
}
