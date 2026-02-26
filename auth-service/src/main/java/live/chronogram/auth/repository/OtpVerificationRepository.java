package live.chronogram.auth.repository;

import java.util.Optional;
import live.chronogram.auth.enums.OtpType;
import live.chronogram.auth.model.OtpVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OtpVerificationRepository extends JpaRepository<OtpVerification, Long> {
    Optional<OtpVerification> findByTargetAndOtpTypeAndOtpCode(String target, OtpType otpType, String otpCode);

    Optional<OtpVerification> findTopByTargetAndOtpTypeOrderByCreatedTimestampDesc(String target, OtpType otpType);

    void deleteByTargetAndOtpType(String target, OtpType otpType);
}
