package live.chronogram.auth.service;

import java.time.LocalDateTime;
import java.util.Optional;
import live.chronogram.auth.enums.OtpType;
import live.chronogram.auth.model.OtpVerification;
import live.chronogram.auth.repository.OtpVerificationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.SecureRandom;

/**
 * Service for generating, storing, and verifying One-Time Passwords (OTP).
 * Includes features like rate limiting, lockout periods, and automatic
 * invalidation of old OTPs.
 */
@Service
public class OtpService {

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(OtpService.class);
    private static final SecureRandom secureRandom = new SecureRandom();

    @Autowired
    private OtpVerificationRepository otpVerificationRepository;

    // Lock map to prevent concurrent OTP requests for the same target
    private static final java.util.Map<String, Object> LOCKS = new java.util.concurrent.ConcurrentHashMap<>();


    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Autowired
    private EmailService emailService;

    /**
     * Post-construction hook: Ensures the 'otp_code' column in the database
     * is large enough to handle potentially hashed or long alphanumeric strings.
     */
    @jakarta.annotation.PostConstruct
    public void fixColumnLength() {
        try {
            logger.info("Executing DDL to increase otp_code column length to 255...");
            // Modification for forward compatibility (e.g., moving to SHA-256 in the
            // future)
            jdbcTemplate.execute("ALTER TABLE otp_verification MODIFY otp_code VARCHAR(255) NOT NULL;");
            logger.info("otp_code column length successfully increased.");
        } catch (Exception e) {
            logger.warn(
                    "Could not alter otp_verification table length: {}",
                    e.getMessage());
        }
    }

    @Value("${app.otp.validity-minutes}")
    private int otpValidityMinutes; // Usually 5 minutes

    @Value("${app.otp.max-attempts}")
    private int maxAttempts; // Max invalid entries before lockout (e.g., 5)

    @Value("${app.otp.lock-duration-minutes}")
    private int lockDurationMinutes; // Duration of temporary lockout (e.g., 15)

    /**
     * Generates a secure 6-digit OTP for a specific target (mobile/email).
     * Prevents rapid re-generation (spam protection) and handles lockout logic.
     * 
     * @param target  The destination identifier (phone or email).
     * @param otpType The context (e.g., REGISTRATION, LOGIN).
     * @return The 6-digit OTP string and sessionId.
     */
    /**
     * Entry point for OTP generation. Handles JVM-level synchronization to prevent
     * concurrent requests for the same target from exhausting connections.
     */
    @Transactional(noRollbackFor = live.chronogram.auth.exception.AuthException.class)
    public String[] generateOtp(String target, OtpType otpType) {
        return generateOtp(target, otpType, false);
    }

    /**
     * Entry point for OTP generation. Handles JVM-level synchronization to prevent
     * concurrent requests for the same target from exhausting connections.
     */
    @Transactional(noRollbackFor = live.chronogram.auth.exception.AuthException.class)
    public String[] generateOtp(String target, OtpType otpType, boolean isResend) {
        // 1. JVM Lock: Prevent concurrent requests for the same target
        Object lock = LOCKS.computeIfAbsent(target, k -> new Object());
        synchronized (lock) {
            try {
                // 1. Fetch the most recent OTP record
                Optional<OtpVerification> existingOpt = otpVerificationRepository
                        .findTopByTargetAndOtpTypeOrderByCreatedTimestampDesc(target, otpType);

                int currentSendCount = 0;
                int currentAttempts = 0;
                String sessionId = java.util.UUID.randomUUID().toString();

                if (existingOpt.isPresent()) {
                    OtpVerification existing = existingOpt.get();

                    // 2. Security Check: Strict 15-minute lockout enforcement
                    if (existing.getLockedUntil() != null && existing.getLockedUntil().isAfter(LocalDateTime.now())) {
                        long minutesLeft = java.time.Duration.between(LocalDateTime.now(), existing.getLockedUntil())
                                .toMinutes();
                        if (minutesLeft == 0)
                            minutesLeft = 1;
                        throw new live.chronogram.auth.exception.AuthException(
                                org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,
                                "Too many OTP requests. Please try again after " + minutesLeft + " minute(s).");
                    }

                    // 3. Persistence: Inherit counts if within the session window (30m)
                    if (Boolean.TRUE.equals(existing.getVerified())
                            || existing.getCreatedTimestamp().plusMinutes(30).isBefore(LocalDateTime.now())
                            || (existing.getLockedUntil() != null
                                    && existing.getLockedUntil().isBefore(LocalDateTime.now()))) {
                        currentSendCount = 0;
                        currentAttempts = 0;
                    } else {
                        currentSendCount = existing.getResendCount() != null ? existing.getResendCount() : 0;
                        currentAttempts = existing.getAttempts() != null ? existing.getAttempts() : 0;
                    }

                    // 4. Total Send Limit (Max 5 per 15-minute window)
                    // We check FIRST before allowing even a reuse
                    if (currentSendCount >= 5) {
                        existing.setLockedUntil(LocalDateTime.now().plusMinutes(lockDurationMinutes));
                        otpVerificationRepository.save(existing);
                        throw new live.chronogram.auth.exception.AuthException(
                                org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,
                                "Maximum OTP generation limit reached. Blocked for " + lockDurationMinutes
                                        + " minutes.");
                    }

                    // 5. Spam Protection & Reuse logic
                    boolean isRecentlySent = existing.getExpiresAt().isAfter(LocalDateTime.now())
                            && (existing.getVerified() == null || !existing.getVerified());

                    // If user clicks "Resend" and an active OTP exists, we return it immediately
                    // BUT we still increment the request count in the DB to track usage
                    if (isResend && isRecentlySent) {
                        existing.setResendCount(currentSendCount + 1);
                        otpVerificationRepository.save(existing);

                        // 🔥 BUG FIX: Actually SEND the email even on resend!
                        if (target != null && target.contains("@")) {
                            emailService.sendOtpEmail(target, existing.getOtpCode());
                        }

                        return new String[] { existing.getOtpCode(), existing.getSessionId() };
                    }

                    // If user clicks "Send" (fresh) but one already exists, make them wait (Spam
                    // Protection)
                    if (!isResend && isRecentlySent) {
                        long secondsLeft = java.time.Duration.between(LocalDateTime.now(), existing.getExpiresAt())
                                .getSeconds();
                        throw new live.chronogram.auth.exception.AuthException(
                                org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,
                                "Please wait " + secondsLeft + " seconds before requesting a new OTP.");
                    }
                }

                // 6. Finalization: Increment send count and generate new code
                currentSendCount++;

                otpVerificationRepository.deleteByTargetAndOtpType(target, otpType);
                String otp = String.format("%06d", secureRandom.nextInt(1_000_000));

                OtpVerification otpVerification = new OtpVerification();
                otpVerification.setTarget(target);
                otpVerification.setOtpType(otpType);
                otpVerification.setOtpCode(otp);
                otpVerification.setExpiresAt(LocalDateTime.now().plusMinutes(otpValidityMinutes));
                otpVerification.setAttempts(currentAttempts);
                otpVerification.setResendCount(currentSendCount);
                otpVerification.setSessionId(sessionId);
                otpVerification.setVerified(false);

                otpVerificationRepository.save(otpVerification);

                if (target != null && target.contains("@")) {
                    emailService.sendOtpEmail(target, otp);
                }
                return new String[] { otp, sessionId };
            } catch (live.chronogram.auth.exception.AuthException e) {
                throw e;
            }
        }
    }


    /**
     * Entry point for OTP verification.
     */
    /**
     * Entry point for OTP verification.
     */
    @Transactional(noRollbackFor = live.chronogram.auth.exception.AuthException.class)
    public boolean verifyOtp(String target, OtpType otpType, String otpCode) {
        Object lock = LOCKS.computeIfAbsent(target, k -> new Object());
        synchronized (lock) {
            // 1. Syntax Validation: OTP must be exactly 6 digits
            if (otpCode == null || !otpCode.trim().matches("^\\d{6}$")) {
                throw new live.chronogram.auth.exception.AuthException(
                        org.springframework.http.HttpStatus.BAD_REQUEST,
                        "Invalid OTP format. It must be a 6-digit number.");
            }

            logger.info("Verifying OTP for Target: [{}] and Type: [{}]", target, otpType);
            // 2. Fetch the latest record
            Optional<OtpVerification> otpOpt = otpVerificationRepository
                    .findTopByTargetAndOtpTypeOrderByCreatedTimestampDesc(target, otpType);

            if (otpOpt.isEmpty()) {
                return false;
            }

            OtpVerification otpVerification = otpOpt.get();

            // 3. Security: Check for temporary lockout
            if (otpVerification.getLockedUntil() != null) {
                if (otpVerification.getLockedUntil().isAfter(LocalDateTime.now())) {
                    throw new live.chronogram.auth.exception.AuthException(
                            org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,
                            "Too many invalid OTP attempts. Please try again later.");
                } else {
                    // Natural Expiry: If the lockout time has passed, reset the status within this
                    // record
                    otpVerification.setLockedUntil(null);
                    otpVerification.setAttempts(0);
                    otpVerificationRepository.save(otpVerification);
                }
            }

            // 4. Integrity Check: Max failed attempts enforcement
            if (otpVerification.getAttempts() >= maxAttempts) {
                if (otpVerification.getLockedUntil() == null) {
                    // Removed: otpVerification.setAttempts(0); 
                    // We should not reset attempts here if they have reached maxAttempts without a lock
                    throw new live.chronogram.auth.exception.AuthException(
                            org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,
                            "Too many invalid OTP attempts. Please try again later.");
                } else {
                    throw new live.chronogram.auth.exception.AuthException(
                            org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,
                            "Too many invalid OTP attempts. Please try again later.");
                }
            }

            // 5. Validity Checks: Expiry and prior verification
            if (otpVerification.getExpiresAt().isBefore(LocalDateTime.now())) {
                throw new live.chronogram.auth.exception.AuthException(
                        org.springframework.http.HttpStatus.BAD_REQUEST,
                        "OTP expired.");
            }

            if (Boolean.TRUE.equals(otpVerification.getVerified())) {
                throw new live.chronogram.auth.exception.AuthException(
                        org.springframework.http.HttpStatus.BAD_REQUEST,
                        "OTP already verified.");
            }

            // 6. Visual Code Comparison
            if (otpCode.equals(otpVerification.getOtpCode())) {
                // Success: Mark as verified and immediately expire to prevent reuse
                otpVerification.setVerified(true);
                otpVerification.setExpiresAt(LocalDateTime.now());
                otpVerificationRepository.save(otpVerification);
                return true;
            } else {
                // 7. Failure: Track failed attempts and trigger lockout if limit reached
                int newAttemptCount = otpVerification.getAttempts() + 1;
                otpVerification.setAttempts(newAttemptCount);

                if (newAttemptCount >= maxAttempts) {
                    // Trigger Lockout
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

}
