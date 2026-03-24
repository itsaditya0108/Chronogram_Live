package live.chronogram.auth.service;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import org.springframework.stereotype.Service;

@Service
public class FirebaseAuthService {

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(FirebaseAuthService.class);
    private final FirebaseAuth firebaseAuth;

    public FirebaseAuthService(FirebaseAuth firebaseAuth) {
        this.firebaseAuth = firebaseAuth;
    }

    /**
     * Verifies a Firebase ID Token and returns the associated phone number.
     * @param idToken The Firebase ID Token from the client.
     * @return The verified phone number.
     * @throws RuntimeException If the token is invalid or expired.
     */
    public String verify(String idToken) {
        try {
            FirebaseToken decoded = firebaseAuth.verifyIdToken(idToken);
            String phoneNumber = (String) decoded.getClaims().get("phone_number");
            if (phoneNumber == null) {
                // Sometimes it's in the sub or another claim depending on how it was verified
                phoneNumber = decoded.getUid(); // Fallback to UID or check other claims
            }
            logger.info("Successfully verified Firebase token for phone: {}", phoneNumber);
            return phoneNumber;
        } catch (Exception e) {
            logger.error("Failed to verify Firebase token: {}", e.getMessage());
            throw new live.chronogram.auth.exception.AuthException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED, 
                    "Invalid or expired Firebase token.");
        }
    }
}
