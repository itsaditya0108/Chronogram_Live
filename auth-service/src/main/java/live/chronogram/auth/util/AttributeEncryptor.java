package live.chronogram.auth.util;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.security.Key;
import java.util.Base64;

/**
 * JPA Attribute Converter for transparent AES encryption of sensitive database
 * columns.
 * Used to protect PII (Personally Identifiable Information) like names, emails,
 * and mobile numbers.
 *
 * <p>
 * Implemented as a {@link Component} to allow Spring to inject the encryption
 * key,
 * though Hibernate creates the actual instance. A static bridge is used for the
 * key.
 */
@Component
@Converter
public class AttributeEncryptor implements AttributeConverter<String, String> {

    private static final String AES = "AES";

    // Injecting via setter or static field might be tricky in Converter
    // Standard approach: use a static holder or configuration
    // For simplicity in this context, we will read a property or default
    // Ideally this should be injected.
    // BUT AttributeConverter is instantiated by Hibernate, not Spring, so @Value
    // doesn't work directly inside it easily without a static accessor.

    private static String SECRET_KEY;

    @Value("${app.security.db-encryption-key}")
    public void setSecretKey(String secretKey) {
        AttributeEncryptor.SECRET_KEY = secretKey;
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null)
            return null;
            
        if (SECRET_KEY == null) {
            // Log a warning if the key is missing (e.g. during Hibernate boot before Spring injection)
            System.err.println("[AttributeEncryptor] ERROR: SECRET_KEY is null during encryption. Baseline plaintext returned.");
            return attribute;
        }
            
        try {
            Key key = new SecretKeySpec(SECRET_KEY.getBytes(java.nio.charset.StandardCharsets.UTF_8), AES);
            Cipher cipher = Cipher.getInstance(AES);
            cipher.init(Cipher.ENCRYPT_MODE, key);
            return Base64.getEncoder().encodeToString(cipher.doFinal(attribute.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception e) {
            System.err.println("[AttributeEncryptor] Encryption failed: " + e.getMessage());
            return attribute; // Fallback to plaintext
        }
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null)
            return null;
            
        if (SECRET_KEY == null) {
            System.err.println("[AttributeEncryptor] ERROR: SECRET_KEY is null during decryption. Returning raw DB data.");
            return dbData;
        }
            
        try {
            Key key = new SecretKeySpec(SECRET_KEY.getBytes(java.nio.charset.StandardCharsets.UTF_8), AES);
            Cipher cipher = Cipher.getInstance(AES);
            cipher.init(Cipher.DECRYPT_MODE, key);
            return new String(cipher.doFinal(Base64.getDecoder().decode(dbData)), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            // Fallback: If decryption fails (e.g. data is plaintext), return as-is
            return dbData;
        }
    }
}
