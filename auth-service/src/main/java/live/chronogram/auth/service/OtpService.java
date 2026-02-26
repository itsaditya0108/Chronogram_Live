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

    /**
     * Generates a secure 6-digit OTP for the given target (e.g., mobile number or
     * email)
     * and specific OTP type. If an active, unverified OTP already exists, this
     * method
     * prevents immediate re-generation to deter abuse.
     * 
     * @param target  The destination identifier (e.g., +919876543210 or
     *                user@example.com)
     * @param otpType The context of the OTP (e.g., MOBILE_LOGIN,
     *                EMAIL_VERIFICATION)
     * @return The generated 6-digit OTP string.
     * @throws RuntimeException If an active OTP requested within the validity
     *                          window already exists.
     */

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public String generateOtp(String target, OtpType otpType) {
        Optional<OtpVerification> existingOpt = otpVerificationRepository
                .findTopByTargetAndOtpTypeOrderByCreatedTimestampDesc(target, otpType);

        if (existingOpt.isPresent()) {
            OtpVerification existing = existingOpt.get();

            if (existing.getLockedUntil() != null && existing.getLockedUntil().isAfter(LocalDateTime.now())) {
                long minutesLeft = java.time.Duration.between(LocalDateTime.now(), existing.getLockedUntil())
                        .toMinutes();
                if (minutesLeft == 0)
                    minutesLeft = 1;
                throw new live.chronogram.auth.exception.AuthException(
                        org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,
                        "Maximum OTP attempts reached for this " + (otpType.name().contains("EMAIL") ? "email" : "mobile number") + ". Please try again after " + minutesLeft + " minute(s).");
            }

            if (existing.getExpiresAt().isAfter(LocalDateTime.now())
                    && (existing.getVerified() == null || !existing.getVerified())) {
                // Calculate remaining seconds
                long secondsLeft = java.time.Duration.between(LocalDateTime.now(), existing.getExpiresAt())
                        .getSeconds();
                throw new live.chronogram.auth.exception.AuthException(
                        org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,
                        "An active OTP already exists. Please wait " + secondsLeft
                                + " seconds before requesting a new one.");
            }
        }

        // Invalidate existing OTPs (e.g. expired or previously verified)
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

    /**
     * Verifies the provided OTP against the latest record in the database for the
     * given target.
     * Incorporates strong validation against empty values, max attempt lockouts,
     * and prevents
     * multiple use of the same successful OTP.
     * 
     * @param target  The destination identifier the OTP was sent to (e.g. mobile or
     *                email).
     * @param otpType The type of OTP being verified.
     * @param otpCode The 6-digit code provided by the user.
     * @return True if the OTP is accurate and within limits; throws exceptions
     *         strictly otherwise.
     * @throws AuthException If the format is invalid, max attempts are reached, or
     *                       the user is locked out.
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW, noRollbackFor = live.chronogram.auth.exception.AuthException.class)
    public boolean verifyOtp(String target, OtpType otpType, String otpCode) {
        if (otpCode == null || !otpCode.trim().matches("^\\d{6}$")) {
            throw new live.chronogram.auth.exception.AuthException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Invalid OTP format. It must be a 6-digit number.");
        }

        Optional<OtpVerification> otpOpt = otpVerificationRepository
                .findTopByTargetAndOtpTypeOrderByCreatedTimestampDesc(target, otpType);

        if (otpOpt.isEmpty()) {
            return false;
        }

        OtpVerification otpVerification = otpOpt.get();

        if (otpVerification.getLockedUntil() != null && otpVerification.getLockedUntil().isAfter(LocalDateTime.now())) {
            throw new live.chronogram.auth.exception.AuthException(
                    org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,
                    "Too many invalid OTP attempts. Please try again later.");
        }

        if (otpVerification.getExpiresAt().isBefore(LocalDateTime.now())) {
            return false;
        }

        if (Boolean.TRUE.equals(otpVerification.getVerified())) {
            return false;
        }

        if (otpVerification.getOtpCode().equals(otpCode)) {
            otpVerification.setVerified(true);
            otpVerification.setExpiresAt(LocalDateTime.now()); // Expire immediately to prevent reuse
            otpVerificationRepository.save(otpVerification);
            return true;
        } else {
            int newAttemptCount = otpVerification.getAttempts() + 1;
            otpVerification.setAttempts(newAttemptCount);

            if (newAttemptCount >= maxAttempts) {
                otpVerification.setLockedUntil(LocalDateTime.now().plusMinutes(lockDurationMinutes));
                otpVerificationRepository.save(otpVerification);
                throw new live.chronogram.auth.exception.AuthException(
                        org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,
                        "Maximum OTP attempts reached (" + maxAttempts + "). Please try again after "
                                + lockDurationMinutes + " minutes.");
            }

            otpVerificationRepository.save(otpVerification);

            int attemptsLeft = maxAttempts - newAttemptCount;
            throw new live.chronogram.auth.exception.AuthException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Invalid OTP. " + attemptsLeft + " attempt" + (attemptsLeft == 1 ? "" : "s") + " remaining.");
        }
    }
}
