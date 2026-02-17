package live.chronogram.auth.service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Random;
import live.chronogram.auth.enums.OtpType;
import live.chronogram.auth.model.OtpVerification;
import live.chronogram.auth.repository.OtpVerificationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OtpService {

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(OtpService.class);

    @Autowired
    private OtpVerificationRepository otpVerificationRepository;

    @Value("${app.otp.validity-minutes}")
    private int otpValidityMinutes;

    @Value("${app.otp.max-attempts}")
    private int maxAttempts;

    @Value("${app.otp.lock-duration-minutes}")
    private int lockDurationMinutes;

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public String generateOtp(String target, OtpType otpType) {
        // Invalidate existing OTPs
        otpVerificationRepository.deleteByTargetAndOtpType(target, otpType);

        String otp = String.format("%06d", new Random().nextInt(999999));

        OtpVerification otpVerification = new OtpVerification();
        otpVerification.setTarget(target);
        otpVerification.setOtpType(otpType);
        otpVerification.setOtpCode(otp);
        otpVerification.setExpiresAt(LocalDateTime.now().plusMinutes(otpValidityMinutes));
        otpVerification.setAttempts(0);
        otpVerification.setVerified(false);

        otpVerificationRepository.save(otpVerification);

        // TODO: Integrate with SMS/Email provider to send OTP
        logger.info("Generated OTP for {}: {}", target, otp);

        return otp;
    }

    @Transactional
    public boolean verifyOtp(String target, OtpType otpType, String otpCode) {
        Optional<OtpVerification> otpOpt = otpVerificationRepository.findByTargetAndOtpType(target, otpType);

        if (otpOpt.isEmpty()) {
            return false;
        }

        OtpVerification otpVerification = otpOpt.get();

        if (otpVerification.getLockedUntil() != null && otpVerification.getLockedUntil().isAfter(LocalDateTime.now())) {
            throw new RuntimeException("Too many attempts. Please try again later.");
        }

        if (otpVerification.getExpiresAt().isBefore(LocalDateTime.now())) {
            return false;
        }

        if (otpVerification.getOtpCode().equals(otpCode)) {
            otpVerification.setVerified(true);
            otpVerificationRepository.save(otpVerification);
            return true;
        } else {
            otpVerification.setAttempts(otpVerification.getAttempts() + 1);
            if (otpVerification.getAttempts() >= maxAttempts) {
                otpVerification.setLockedUntil(LocalDateTime.now().plusMinutes(lockDurationMinutes));
            }
            otpVerificationRepository.save(otpVerification);
            return false;
        }
    }
}
