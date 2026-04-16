package com.company.video_service.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import java.security.Key;

public class JwtUtil {

    private final Key signingKey;

    public JwtUtil(String secret) {
        // Decode the secret using BASE64, matching the Auth and Image services.
        // This resolves the 'JWT signature does not match' error.
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public Claims validateAndGetClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(signingKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
