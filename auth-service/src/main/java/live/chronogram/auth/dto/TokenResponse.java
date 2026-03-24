package live.chronogram.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Data Transfer Object containing authentication tokens (Access and Refresh).
 * Returned upon successful login, registration completion, or token refresh.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TokenResponse {
    /**
     * Short-lived Access Token (JWT).
     * Logic: Signed with the secret key and contains user identity. Expire in 1 hour usually.
     */
    private String accessToken;

    /**
     * Long-lived Refresh Token (Hashed in DB).
     * Logic: Exchanged for new Access Tokens. Valid for 30 days usually.
     */
    private String refreshToken;

    /**
     * The type of token.
     * Logic: Always "Bearer" to follow OAuth2 standards.
     */
    private String tokenType = "Bearer";

    /**
     * Optional status/error message for the frontend to display.
     */
    private String message;

    public TokenResponse(String accessToken, String refreshToken) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
    }

    public TokenResponse(String accessToken, String refreshToken, String message) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.message = message;
    }

    // Getters and Setters
    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
