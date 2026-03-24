package live.chronogram.auth.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.security.Key;
import java.util.Date;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Utility class for generating and validating JSON Web Tokens (JWT).
 * Handles access tokens, refresh tokens, registration tokens, and OTP session
 * tokens.
 */
@Component
public class JwtTokenProvider {

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.access-token-validity-ms}")
    private long accessTokenValidityInMs;

    @Value("${app.jwt.refresh-token-validity-ms}")
    private long refreshTokenValidityInMs;

    @Value("${app.jwt.registration-token-validity-ms}")
    private long registrationTokenValidityInMs;

    @Value("${app.jwt.otp-session-token-validity-ms}")
    private long otpSessionTokenValidityInMs;

    /**
     * Generates a signing key from the base64 encoded secret.
     */
    private Key getSignInKey() {
        byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Creates a new access token for a user.
     * 
     * @param userId       The ID of the authenticated user.
     * @param role         The user's assigned role.
     * @param userDeviceId The ID of the device being used.
     * @return A signed JWT access token.
     */
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

    /**
     * Creates a new refresh token for a user.
     * 
     * @param userId The ID of the user.
     * @return A signed JWT refresh token.
     */
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

    // Strict Base64Url pattern for JWT parts (no padding =)
    private static final java.util.regex.Pattern JWT_STRICT_PATTERN = java.util.regex.Pattern.compile(
            "^[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+$");

    /**
     * Checks if a token follows the strict JWT format without padding.
     */
    private boolean isStrictlyFormattedJwt(String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }
        return JWT_STRICT_PATTERN.matcher(token).matches();
    }

    /**
     * Extracts the user ID from a given JWT.
     * 
     * @param token The JWT token string.
     * @return The user ID as a Long.
     * @throws AuthException if the token is malformed or invalid.
     */
    public Long getUserIdFromToken(String token) {
        if (!isStrictlyFormattedJwt(token)) {
            throw new live.chronogram.auth.exception.AuthException(org.springframework.http.HttpStatus.UNAUTHORIZED,
                    "Malformed JWT structure");
        }

        try {
            Claims claims = Jwts.parser()
                    .verifyWith((javax.crypto.SecretKey) getSignInKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            return Long.parseLong(claims.getSubject());
        } catch (io.jsonwebtoken.JwtException ex) {
            throw new live.chronogram.auth.exception.AuthException(org.springframework.http.HttpStatus.UNAUTHORIZED,
                    "Invalid JWT Signature or Token");
        }
    }

    /**
     * Extracts the user role from a given JWT.
     * 
     * @param token The JWT token string.
     * @return The role name as a String.
     */
    public String getRoleFromToken(String token) {
        if (!isStrictlyFormattedJwt(token)) {
            throw new live.chronogram.auth.exception.AuthException(org.springframework.http.HttpStatus.UNAUTHORIZED,
                    "Malformed JWT structure");
        }

        try {
            Claims claims = Jwts.parser()
                    .verifyWith((javax.crypto.SecretKey) getSignInKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            return (String) claims.get("role");
        } catch (io.jsonwebtoken.JwtException ex) {
            throw new live.chronogram.auth.exception.AuthException(org.springframework.http.HttpStatus.UNAUTHORIZED,
                    "Invalid JWT Signature or Token");
        }
    }

    /**
     * Validates a JWT's signature and expiration.
     * 
     * @param authToken The token to validate.
     * @return true if valid, false otherwise.
     */
    public boolean validateToken(String authToken) {
        if (!isStrictlyFormattedJwt(authToken)) {
            System.out.println("Invalid JWT Signature/Token format: Trailing/padded char found");
            return false;
        }

        try {
            Jwts.parser()
                    .verifyWith((javax.crypto.SecretKey) getSignInKey())
                    .build()
                    .parseSignedClaims(authToken);
            return true;
        } catch (io.jsonwebtoken.JwtException ex) {
            // Invalid, Expired, Unsupported, or Malformed JWT token (including bad
            // signatures)
            System.out.println("Invalid JWT Signature/Token: " + ex.getMessage());
        } catch (IllegalArgumentException ex) {
            // JWT claims string is empty
        }
        return false;
    }

    /**
     * Creates a temporary token used during the registration flow.
     * This token is 'statelessly' passed between registration steps (Mobile -> Email -> Profile).
     * 
     * @param mobileNumber The user's mobile number.
     * @param email        The user's email address.
     * @param step         The current registration step (e.g., EMAIL_VERIFIED).
     * @param sessionId    The unique ID of the OTP session.
     * @return A signed registration JWT.
     */
    public String createRegistrationToken(String mobileNumber, String email, String step, String sessionId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + registrationTokenValidityInMs);

        return Jwts.builder()
                .subject(mobileNumber)
                .claim("type", "REGISTRATION")
                .claim("step", step)
                .claim("email", email)
                .claim("sessionId", sessionId)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSignInKey()) // Use same key helper
                .compact();
    }

    /**
     * Decodes and returns claims from a registration token.
     */
    public Claims getClaimsFromRegistrationToken(String token) {
        if (!isStrictlyFormattedJwt(token)) {
            throw new live.chronogram.auth.exception.AuthException(org.springframework.http.HttpStatus.UNAUTHORIZED,
                    "Malformed Registration Token");
        }

        try {
            return Jwts.parser()
                    .verifyWith((javax.crypto.SecretKey) getSignInKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception ex) {
            throw new live.chronogram.auth.exception.AuthException(org.springframework.http.HttpStatus.UNAUTHORIZED,
                    "Invalid Registration Token");
        }
    }

    /**
     * Decodes and returns claims from any valid signed JWT.
     */
    public Claims getClaimsFromAnyToken(String token) {
        if (!isStrictlyFormattedJwt(token)) {
            throw new live.chronogram.auth.exception.AuthException(org.springframework.http.HttpStatus.UNAUTHORIZED,
                    "Malformed Token Structure");
        }
        try {
            return Jwts.parser()
                    .verifyWith((javax.crypto.SecretKey) getSignInKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (io.jsonwebtoken.JwtException ex) {
            throw new live.chronogram.auth.exception.AuthException(org.springframework.http.HttpStatus.UNAUTHORIZED,
                    "Invalid Signature or Token");
        }
    }

    /**
     * Creates a session token for OTP verification.
     * This token binds the mobile number and device ID to a specific OTP session.
     * 
     * @param mobileNumber The user's mobile number.
     * @param deviceId     The ID of the device.
     * @param sessionId    The unique ID of the OTP session.
     * @return A signed OTP session JWT.
     */
    public String createOtpSessionToken(String mobileNumber, String deviceId, String sessionId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + otpSessionTokenValidityInMs);

        return Jwts.builder()
                .subject(mobileNumber)
                .claim("type", "OTP_SESSION")
                .claim("deviceId", deviceId)
                .claim("sessionId", sessionId)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSignInKey())
                .compact();
    }

    /**
     * Decodes and returns claims from an OTP session token.
     * @param allowExpired if true, returns claims even if the token has expired (as long as signature is valid).
     */
    public Claims getClaimsFromOtpSessionToken(String token, boolean allowExpired) {
        if (!isStrictlyFormattedJwt(token)) {
            throw new live.chronogram.auth.exception.AuthException(org.springframework.http.HttpStatus.UNAUTHORIZED,
                    "Malformed OTP Session Token");
        }

        try {
            return Jwts.parser()
                    .verifyWith((javax.crypto.SecretKey) getSignInKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException ex) {
            if (allowExpired) {
                return ex.getClaims(); // Signature is valid, just expired.
            }
            throw new live.chronogram.auth.exception.AuthException(org.springframework.http.HttpStatus.UNAUTHORIZED,
                    "Expired OTP Session Token");
        } catch (Exception ex) {
            throw new live.chronogram.auth.exception.AuthException(org.springframework.http.HttpStatus.UNAUTHORIZED,
                    "Invalid OTP Session Token");
        }
    }

    /**
     * Decodes and returns claims from an OTP session token (strict expiration).
     */
    public Claims getClaimsFromOtpSessionToken(String token) {
        return getClaimsFromOtpSessionToken(token, false);
    }
}
