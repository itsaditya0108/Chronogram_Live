package live.chronogram.auth.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.security.Key;
import java.util.Date;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.access-token-validity-ms}")
    private long accessTokenValidityInMs;

    @Value("${app.jwt.refresh-token-validity-ms}")
    private long refreshTokenValidityInMs;

    private Key getSignInKey() {
        byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String createAccessToken(Long userId, String role, Long userDeviceId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + accessTokenValidityInMs);

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("role", role)
                .claim("deviceId", userDeviceId != null ? userDeviceId.toString() : null)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSignInKey())
                .compact();
    }

    public String createRefreshToken(Long userId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + refreshTokenValidityInMs);

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSignInKey())
                .compact();
    }

    public Long getUserIdFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith((javax.crypto.SecretKey) getSignInKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return Long.parseLong(claims.getSubject());
    }

    public String getRoleFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith((javax.crypto.SecretKey) getSignInKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return (String) claims.get("role");
    }

    public boolean validateToken(String authToken) {
        try {
            Jwts.parser()
                    .verifyWith((javax.crypto.SecretKey) getSignInKey())
                    .build()
                    .parseSignedClaims(authToken);
            return true;
        } catch (MalformedJwtException ex) {
            // Invalid JWT token
        } catch (ExpiredJwtException ex) {
            // Expired JWT token
        } catch (UnsupportedJwtException ex) {
            // Unsupported JWT token
        } catch (IllegalArgumentException ex) {
            // JWT claims string is empty
        }
        return false;
    }

    public String createRegistrationToken(String mobileNumber, String email, String step) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + 900000); // 15 minutes

        return Jwts.builder()
                .subject(mobileNumber)
                .claim("type", "REGISTRATION")
                .claim("step", step)
                .claim("email", email)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSignInKey()) // Use same key helper
                .compact();
    }

    public Claims getClaimsFromRegistrationToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith((javax.crypto.SecretKey) getSignInKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        if (!"REGISTRATION".equals(claims.get("type"))) {
            throw new RuntimeException("Invalid token type");
        }
        return claims;
    }
}
