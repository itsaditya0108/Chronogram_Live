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
            jdbcTemplate.execute("ALTER TABLE otp_verification MODIFY otp_code VARCHAR(255) NOT NULL;");
            logger.info("otp_code column length successfully increased.");
        } catch (Exception e) {
            logger.warn("Could not alter otp_verification table length: {}", e.getMessage());
        }
    }

    @Value("${app.otp.validity-minutes}")
    private int otpValidityMinutes;

    @Value("${app.otp.max-attempts}")
    private int maxAttempts;

    @Value("${app.otp.lock-duration-minutes}")
    private int lockDurationMinutes;

    @Transactional(noRollbackFor = live.chronogram.auth.exception.AuthException.class)
    public String[] generateOtp(String target, OtpType otpType) {
        return generateOtp(target, otpType, false, false);
    }

    @Transactional(noRollbackFor = live.chronogram.auth.exception.AuthException.class)
    public String[] generateOtp(String target, OtpType otpType, boolean isResend) {
        return generateOtp(target, otpType, isResend, false);
    }

    @Transactional(noRollbackFor = live.chronogram.auth.exception.AuthException.class)
    public String[] generateOtp(String target, OtpType otpType, boolean isResend, boolean skipGeneration) {
        Object lock = LOCKS.computeIfAbsent(target, k -> new Object());
        synchronized (lock) {
            try {
                Optional<OtpVerification> existingOpt = otpVerificationRepository
                        .findTopByTargetOrderByCreatedTimestampDesc(target);

                Optional<OtpVerification> globalLock = otpVerificationRepository
                        .findTopByTargetAndLockedUntilAfterOrderByLockedUntilDesc(target, LocalDateTime.now());

                if (globalLock.isPresent()) {
                    long minutesLeft = java.time.Duration.between(LocalDateTime.now(), globalLock.get().getLockedUntil()).toMinutes();
                    if (minutesLeft == 0) minutesLeft = 1;
                    throw new live.chronogram.auth.exception.AuthException(
                            org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,
                            "Too many requests. For your security, this phone number or email is temporarily locked for " + minutesLeft + " minutes.");
                }

                int currentSendCount = 0;
                int currentAttempts = 0;
                String sessionId = java.util.UUID.randomUUID().toString();
                OtpVerification otpVerification;

                if (existingOpt.isPresent()) {
                    otpVerification = existingOpt.get();

                    if (otpVerification.getCreatedTimestamp().plusMinutes(30).isBefore(LocalDateTime.now(java.time.ZoneOffset.UTC))) {
                        logger.info("Existing record for {} is older than 30 minutes. Resetting counters for a fresh session.", target);
                        currentSendCount = 0;
                        currentAttempts = 0;
                        otpVerification.setLockedUntil(null); // Clear any old locks
                        otpVerification.setVerified(false);
                    } else {
                        currentSendCount = otpVerification.getResendCount() != null ? otpVerification.getResendCount() : 0;
                        currentAttempts = otpVerification.getAttempts() != null ? otpVerification.getAttempts() : 0;
                        logger.info("Found existing OTP session for {}. Current sends: {}, current invalid attempts: {}", 
                            target, currentSendCount, currentAttempts);
                    }

                    if (currentSendCount >= 3) {
                        logger.warn("Target {} reached max sends ({}). Blocking for 120 minutes.", target, currentSendCount);
                        if (otpVerification.getLockedUntil() == null || otpVerification.getLockedUntil().isBefore(LocalDateTime.now(java.time.ZoneOffset.UTC))) {
                            otpVerification.setLockedUntil(LocalDateTime.now(java.time.ZoneOffset.UTC).plusMinutes(120));
                            otpVerificationRepository.save(otpVerification);
                        }
                        throw new live.chronogram.auth.exception.AuthException(
                                org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,
                                "You have reached the maximum number of OTP requests. Please try again after 2 hours for security.");
                    }

                    // We now ALWAYS increment currentSendCount regardless of expiry if we are in this block
                    currentSendCount++;
                    otpVerification.setResendCount(currentSendCount);

                    int dynamicValidityMinutes = 2; 
                    if (currentSendCount == 2) dynamicValidityMinutes = 3;
                    else if (currentSendCount == 3) dynamicValidityMinutes = 5;
                    
                    logger.info("{} requested for {} (Attempt {}). New validity: {} minutes.", 
                        isResend ? "Resending OTP" : "Refreshing OTP", target, currentSendCount, dynamicValidityMinutes);

                    String otp = skipGeneration ? "EXTERNAL_FIREBASE" : String.format("%06d", secureRandom.nextInt(1_000_000));
                    otpVerification.setOtpCode(otp);
                    otpVerification.setExpiresAt(LocalDateTime.now(java.time.ZoneOffset.UTC).plusMinutes(dynamicValidityMinutes));
                    otpVerificationRepository.save(otpVerification);

                    if (target != null && target.contains("@") && !skipGeneration) {
                        emailService.sendOtpEmail(target, otpVerification.getOtpCode(), dynamicValidityMinutes);
                    }

                    int attemptsRemaining = Math.max(0, 3 - currentSendCount);
                    return new String[] { otpVerification.getOtpCode(), otpVerification.getSessionId(), 
                        String.valueOf(dynamicValidityMinutes), String.valueOf(attemptsRemaining) };
                } else {
                    logger.info("No record found for {}. Creating new OTP verification record.", target);
                    otpVerification = new OtpVerification();
                    otpVerification.setTarget(target);
                    otpVerification.setOtpType(otpType);
                    otpVerification.setVerified(false);
                }

                currentSendCount++;
                int dynamicValidityMinutes = 2;
                if (currentSendCount == 2) dynamicValidityMinutes = 3;
                else if (currentSendCount == 3) dynamicValidityMinutes = 5;

                logger.info("Generating fresh OTP for {} (Attempt {}). Validity: {} minutes.", 
                    target, currentSendCount, dynamicValidityMinutes);

                String otp = skipGeneration ? "EXTERNAL_FIREBASE" : String.format("%06d", secureRandom.nextInt(1_000_000));

                otpVerification.setOtpCode(otp);
                otpVerification.setExpiresAt(LocalDateTime.now(java.time.ZoneOffset.UTC).plusMinutes(dynamicValidityMinutes));
                otpVerification.setAttempts(currentAttempts);
                otpVerification.setResendCount(currentSendCount);
                if (otpVerification.getSessionId() == null) otpVerification.setSessionId(sessionId);
                otpVerification.setVerified(false);

                otpVerificationRepository.save(otpVerification);
                
                if (target != null && target.contains("@") && !skipGeneration) {
                    emailService.sendOtpEmail(target, otp, dynamicValidityMinutes);
                }

                int attemptsRemaining = Math.max(0, 3 - currentSendCount);
                return new String[] { otp, otpVerification.getSessionId(), String.valueOf(dynamicValidityMinutes), String.valueOf(attemptsRemaining) };
            } catch (live.chronogram.auth.exception.AuthException e) {
                throw e;
            }
        }
    }

    @Transactional(noRollbackFor = live.chronogram.auth.exception.AuthException.class)
    public boolean verifyOtp(String target, OtpType otpType, String otpCode) {
        Object lock = LOCKS.computeIfAbsent(target, k -> new Object());
        synchronized (lock) {
            logger.info("OTP Verification started for target: [{}], type: [{}]", target, otpType);
            Optional<OtpVerification> otpOpt = otpVerificationRepository
                    .findTopByTargetAndOtpTypeOrderByCreatedTimestampDesc(target, otpType);

            if (otpOpt.isEmpty()) {
                logger.warn("Verification failed: No OTP record found for {}", target);
                return false;
            }

            OtpVerification otpVerification = otpOpt.get();

            if (otpVerification.getLockedUntil() != null && otpVerification.getLockedUntil().isAfter(LocalDateTime.now(java.time.ZoneOffset.UTC))) {
                long minutesLeft = java.time.Duration.between(LocalDateTime.now(java.time.ZoneOffset.UTC), otpVerification.getLockedUntil()).toMinutes();
                if (minutesLeft == 0) minutesLeft = 1;
                logger.warn("Verification blocked: Target {} is currently locked for {} more minutes.", target, minutesLeft);
                throw new live.chronogram.auth.exception.AuthException(
                        org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,
                        "Too many unsuccessful verification attempts. This phone number or email is temporarily locked for " + minutesLeft + " minutes.");
            }

            if (otpVerification.getExpiresAt().isBefore(LocalDateTime.now(java.time.ZoneOffset.UTC))) {
                logger.warn("Verification failed: OTP for {} has expired.", target);
                throw new live.chronogram.auth.exception.AuthException(
                        org.springframework.http.HttpStatus.BAD_REQUEST,
                        "The verification code has expired. Please request a new one.");
            }

            if (Boolean.TRUE.equals(otpVerification.getVerified())) {
                logger.warn("Verification failed: OTP for {} was already used.", target);
                throw new live.chronogram.auth.exception.AuthException(
                        org.springframework.http.HttpStatus.BAD_REQUEST,
                        "OTP already verified.");
            }

            if (otpCode.equals(otpVerification.getOtpCode())) {
                logger.info("Verification SUCCESS for target: {}", target);
                otpVerification.setVerified(true);
                otpVerification.setExpiresAt(LocalDateTime.now(java.time.ZoneOffset.UTC));
                otpVerificationRepository.save(otpVerification);
                return true;
            } else {
                int newAttemptCount = otpVerification.getAttempts() + 1;
                otpVerification.setAttempts(newAttemptCount);
                int attemptsLeft = Math.max(0, 3 - newAttemptCount);
                logger.warn("Verification FAILED for {}. Invalid attempts: {}/{}. Remaining: {}", 
                    target, newAttemptCount, 3, attemptsLeft);

                if (newAttemptCount >= 3) {
                    logger.error("Target {} reached max invalid attempts. Locking target.", target);
                    otpVerification.setLockedUntil(LocalDateTime.now(java.time.ZoneOffset.UTC).plusMinutes(120));
                    otpVerificationRepository.save(otpVerification);
                    throw new live.chronogram.auth.exception.AuthException(
                            org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,
                            "Maximum verification attempts reached. Please try again after 2 hours for security.");
                }

                otpVerificationRepository.save(otpVerification);
                throw new live.chronogram.auth.exception.AuthException(
                        org.springframework.http.HttpStatus.BAD_REQUEST,
                        "Incorrect OTP. " + attemptsLeft + " attempts remaining.");
            }
        }
    }
}
