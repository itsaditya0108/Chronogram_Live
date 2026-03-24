package live.chronogram.auth.repository;

import java.util.Optional;
import live.chronogram.auth.enums.OtpType;
import live.chronogram.auth.model.OtpVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


 /** Handles persistence of OTP codes, attempt counts, and lockouts.
 */
@Repository
public interface OtpVerificationRepository extends JpaRepository<OtpVerification, Long> {
    /**
     * Finds an OTP record matching the target, type, and code.
     */
    Optional<OtpVerification> findByTargetAndOtpTypeAndOtpCode(String target, OtpType otpType, String otpCode);

    /**
     * Retrieves the latest OTP generated for a target and type, with a pessimistic
     * lock
     * to prevent race conditions during verification/incrementing attempts.
     */
    Optional<OtpVerification> findTopByTargetAndOtpTypeOrderByCreatedTimestampDesc(String target, OtpType otpType);

    /**
     * Retrieves the latest OTP generated for a target and type WITHOUT locking.
     * Used for non-destructive session verification.
     */
    Optional<OtpVerification> findFirstByTargetAndOtpTypeOrderByCreatedTimestampDesc(String target, OtpType otpType);

    /**
     * Deletes all previous OTP records for a target and type to ensure only one is
     * active.
     */
    void deleteByTargetAndOtpType(String target, OtpType otpType);
}
