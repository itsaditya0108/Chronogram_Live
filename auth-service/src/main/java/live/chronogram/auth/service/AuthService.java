package live.chronogram.auth.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import live.chronogram.auth.enums.OtpType;
import live.chronogram.auth.model.*;
import live.chronogram.auth.dto.*;
import live.chronogram.auth.repository.*;
import live.chronogram.auth.exception.AuthException;
import live.chronogram.auth.security.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.slf4j.MDC;

/**
 * Core business logic for authentication, user management, and session
 * handling.
 * Orchestrates OTP services, device trust, and JWT token generation.
 */
@Service
public class AuthService {

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(AuthService.class);
    private static final org.slf4j.Logger flowLogger = org.slf4j.LoggerFactory.getLogger("live.chronogram.auth.ms");

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserStatusRepository userStatusRepository;

    @Autowired
    private UserSessionRepository userSessionRepository;

    @Autowired
    private UserDeviceRepository userDeviceRepository;

    @Autowired
    private OtpService otpService;

    @Autowired
    private DeviceService deviceService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private live.chronogram.auth.repository.LoginHistoryRepository loginHistoryRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private live.chronogram.auth.repository.OtpVerificationRepository otpVerificationRepository;

    @Autowired
    private IncompleteRegistrationRepository incompleteRegistrationRepository;

    @Autowired
    private FirebaseAuthService firebaseAuthService;

    @Autowired
    private RestTemplate restTemplate;

    @Value("${image.service.url}")
    private String imageServiceUrl;

    @Value("${video.service.url}")
    private String videoServiceUrl;

    @Autowired
    private RegistrationProgressRepository registrationProgressRepository;

    @Autowired
    private StorageUsageRepository storageUsageRepository;

    @Autowired
    private SyncStatusRepository syncStatusRepository;

    @Value("${app.jwt.refresh-token-validity-ms}")
    private long refreshTokenValidityInMs;

    @Value("${app.otp.backend-mobile-generation-enabled:true}")
    private boolean backendMobileOtpEnabled;

    @Value("${app.otp.lock-duration-minutes:120}")
    private int lockDurationMinutes;

    /**
     * Hashes a refresh token using SHA-256 before storing it in the database.
     * 
     * @param token The raw refresh token string.
     * @return The hex-encoded SHA-256 hash.
     */
    private String hashRefreshToken(String token) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(2 * hash.length);
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Could not hash token", e);
        }
    }

    /**
     * Sends an OTP to the given mobile number for login or registration.
     * Ensures user existence based on the attempt type.
     * 
     * @param mobileNumber   The user's mobile number.
     * @param isLoginAttempt True if attempting to log in, false if registering.
     * @return The generated OTP string.
     * @throws AuthException If user exists during registration, or not found during
     *                       login.
     */
    @Transactional(noRollbackFor = AuthException.class)
    public String[] sendOtp(live.chronogram.auth.dto.OtpRequest request, boolean isLoginAttempt) {
        String mobileNumber = request.getMobileNumber();
        String deviceId = request.getDeviceId();
        // 1. Normalize the phone number format (remove non-digits, add country code if
        // missing)
        String sanitizedMobile = sanitizePhoneNumber(mobileNumber);

        // 2. Check if the user already exists in the database
        logger.info("[AuthService] Checking account status for mobile: {} (Login mode: {}, skipSms: {})",
                sanitizedMobile, isLoginAttempt, request.getSkipSms());
        Optional<User> userOpt = userRepository.findByMobileNumber(sanitizedMobile);

        // 2. Upfront Security Checks for Existing Users
        // We perform these checks FIRTS to ensure blocked/deleted users cannot even
        // trigger Firebase SMS
        if (userOpt.isPresent()) {
            User user = userOpt.get();

            // Status: DELETED
            if (Boolean.TRUE.equals(user.getIsDeleted())) {
                throw new AuthException(HttpStatus.GONE,
                        "This account has been deleted. Please contact support.");
            }

            // Status: ADMIN APPROVAL PENDING
            if (!"APPROVED".equals(user.getApprovalStatus())) {
                throw new AuthException(HttpStatus.FORBIDDEN,
                        "Admin approval required. Please wait for sometimes and then try to login again.");
            }

            // Status: BLOCKED or other restricted statuses
            String statusId = user.getUserStatus() != null ? user.getUserStatus().getUserStatusId() : "ACTIVE";
            if ("BLOCK".equals(statusId)) {
                throw new AuthException(HttpStatus.FORBIDDEN,
                        "Account is blocked. Reason: "
                                + (user.getStatusReason() != null ? user.getStatusReason() : "Contact support."));
            }

            // Status: TEMPORARY LOCKOUT
            if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now())) {
                long minutesLeft = java.time.Duration.between(LocalDateTime.now(), user.getLockedUntil()).toMinutes();
                throw new AuthException(HttpStatus.TOO_MANY_REQUESTS,
                        "Account is temporarily locked. Try again after " + (minutesLeft == 0 ? 1 : minutesLeft)
                                + " minute(s).");
            }

            // 2.1 FLOW SPECIFIC CHECKS
            if (isLoginAttempt) {
                // If it's a Login attempt, verify profile is actually complete
                if (isProfileIncomplete(user)) {
                    throw new AuthException(HttpStatus.FORBIDDEN,
                            "Registration incomplete. Please use the registration screen to finish your profile.");
                }
            } else {
                // If it's a Registration attempt for an existing user
                if (!isProfileIncomplete(user)) {
                    // Block registration for existing users (even for Firebase flows)
                    throw new AuthException(HttpStatus.CONFLICT,
                            "You are already registered. Please go back to the login screen and sign in.");
                }
            }
        } else {
            // New User Checks
            if (isLoginAttempt) {
                throw new AuthException(HttpStatus.NOT_FOUND, "User not found. Please register.");
            }
            // Track potential new user registration
            saveIncompleteRegistration(sanitizedMobile, "OTP_SENT", null, null,
                    deviceId, request.getDeviceName(), request.getDeviceModel(),
                    request.getOsName(), request.getOsVersion(), request.getIpAddress(),
                    request.getLatitude(), request.getLongitude(), request.getCity(), request.getCountry());
        }

        // 3. skipSms flag: When Firebase handles SMS delivery on the client side,
        // the caller sets skipSms=true to suppress the OTP echo and skip MySQL storage.
        boolean skipSms = Boolean.TRUE.equals(request.getSkipSms());

        String otpCode;
        String sessionId;
        String expiresInMinutes = "2";
        String attemptsRemaining = "2";

        // Case: Standard or Firebase flow. 
        // We now utilize OtpService even for Firebase flows to track cooldown/rate-limits.
        String[] otpResponse = generateOtpWithLockout(sanitizedMobile, OtpType.MOBILE_LOGIN, userOpt, false, skipSms);
        otpCode = (skipSms && !backendMobileOtpEnabled) ? "" : otpResponse[0];
        sessionId = otpResponse[1];
        expiresInMinutes = otpResponse[2];
        attemptsRemaining = otpResponse[3];

        if (skipSms) {
            logger.info("Firebase flow detected (skipSms=true). Using shell OTP for metadata tracking for: {}",
                    sanitizedMobile);
        }

        // 4. Create a short-lived JWT that binds the phone number, deviceId, and
        // sessionId together
        // This token must be sent back in the 'verify-otp' step to prove session
        // continuity.
        String sessionToken = jwtTokenProvider.createOtpSessionToken(sanitizedMobile, deviceId, sessionId);

        updateRegistrationProgressForNewUser(sanitizedMobile, "OTP_SENT");
        return new String[] { otpCode, sessionToken, expiresInMinutes, attemptsRemaining };
    }

    private void updateRegistrationProgressForNewUser(String mobile, String step) {
        userRepository.findByMobileNumber(mobile).ifPresent(user -> {
            updateRegistrationProgress(user.getUserId(), step);
            user.setRegistrationStatus(step);
            userRepository.save(user);
        });
    }

    /**
     * Resends an OTP to the given mobile number for login or registration.
     * Unlike {@link #sendOtp}, this bypasses the "active OTP still exists" spam
     * check so a new OTP is always sent immediately when the user requests resend.
     *
     * @param mobileNumber   The user's mobile number.
     * @param isLoginAttempt True if resending for login, false for registration.
     * @param deviceId       The caller's device ID (for session token binding).
     * @return Array of [otpCode, otpSessionToken].
     */
    /**
     * Entry point for resending OTP.
     * Non-transactional to avoid holding connections while locked or performing
     * basic validation.
     * 
     * @param mobileNumber   The sanitized or raw mobile number.
     * @param isLoginAttempt True if this is for an existing user login.
     * @param deviceId       The ID of the device requesting the resend.
     * @return Array of [otpCode, otpSessionToken].
     */
    @Transactional(noRollbackFor = AuthException.class)
    public String[] resendOtp(String mobileNumber, boolean isLoginAttempt, String deviceId, boolean skipSms) {
        // 1. Normalize the mobile number (No DB needed)
        String sanitizedMobile = sanitizePhoneNumber(mobileNumber);

        // 2. Identify the user and perform initial state checks (Transactional)
        Optional<User> userOpt = findAndValidateUserForResend(sanitizedMobile, isLoginAttempt);

        // 3. Generate a NEW OTP code (Synchronization handled inside OtpService)
        String[] otpResponse = generateOtpWithLockout(sanitizedMobile, OtpType.MOBILE_LOGIN, userOpt, true, skipSms);
        String otpCode = (skipSms && !backendMobileOtpEnabled) ? "" : otpResponse[0];
        String sessionId = otpResponse[1];
        String expiresInMinutes = otpResponse[2];
        String attemptsRemaining = otpResponse[3];

        // 4. Issue the session token binding the mobile and device
        String sessionToken = jwtTokenProvider.createOtpSessionToken(sanitizedMobile, deviceId, sessionId);

        return new String[] { otpCode, sessionToken, expiresInMinutes, attemptsRemaining };
    }

    /**
     * Transactional worker for user validation.
     */
    @Transactional
    protected Optional<User> findAndValidateUserForResend(String sanitizedMobile, boolean isLoginAttempt) {
        // 2. Identify the user
        Optional<User> userOpt = userRepository.findByMobileNumber(sanitizedMobile);
        if (isLoginAttempt) {
            // LOGIN RESEND: User must exist
            if (userOpt.isEmpty()) {
                throw new AuthException(HttpStatus.NOT_FOUND,
                        "User not found. Please register.");
            }
            User user = userOpt.get();
            // Verify profile completeness
            if (user.getEmail() == null || user.getName() == null) {
                throw new AuthException(HttpStatus.FORBIDDEN,
                        "Registration incomplete. Please register again.");
            }
            // Verify account status
            String statusId = user.getUserStatus() != null ? user.getUserStatus().getUserStatusId() : "ACTIVE";
            if (!"ACTIVE".equals(statusId) && !"BLOCK".equals(statusId)) {
                String statusName = user.getUserStatus() != null ? user.getUserStatus().getName() : "Unknown";
                throw new AuthException(HttpStatus.FORBIDDEN,
                        "Account is " + statusName + ". Cannot resend OTP.");
            }
            // Handle logical lockouts
            if (user.getLockedUntil() != null) {
                if (user.getLockedUntil().isAfter(LocalDateTime.now())) {
                    long minutesLeft = java.time.Duration.between(LocalDateTime.now(), user.getLockedUntil())
                            .toMinutes();
                    if (minutesLeft == 0)
                        minutesLeft = 1;
                    throw new AuthException(
                            HttpStatus.TOO_MANY_REQUESTS,
                            "Your account is temporarily locked. Please try again after " + minutesLeft
                                    + " minute(s).");
                } else {
                    // Lock expired, reset it
                    user.setLockedUntil(null);
                    userRepository.save(user);
                }
            }
        } else {
            // REGISTRATION RESEND: User should not already be fully registered
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                if (!isProfileIncomplete(user)) {
                    throw new AuthException(HttpStatus.CONFLICT,
                            "You are already registered. Please go back to the login screen and sign in.");
                }
            }
        }
        return userOpt;
    }

    /**
     * Sends an Email OTP primarily for recovery or linking where the User already
     * exists.
     * 
     * @param mobileNumber The user's registered mobile number used to look up their
     *                     email.
     * @return The generated OTP string sent to the email.
     * @throws RuntimeException If user or email is not found.
     */
    @Transactional(noRollbackFor = AuthException.class)
    public String[] sendEmailOtp(String mobileNumber) {
        String sanitizedMobile = sanitizePhoneNumber(mobileNumber);

        User user = userRepository.findByMobileNumber(sanitizedMobile)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now())) {
            long minutesLeft = java.time.Duration.between(LocalDateTime.now(), user.getLockedUntil())
                    .toMinutes();
            if (minutesLeft == 0)
                minutesLeft = 1;
            throw new AuthException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Account locked. Please try again after " + minutesLeft + " minute(s).");
        }

        return generateOtpWithLockout(user.getEmail(), OtpType.EMAIL_VERIFICATION, Optional.of(user));
    }

    /**
     * Sends an Email OTP for a new registration flow stateless step, identified by
     * token.
     * 
     * @param email             The email address to verify.
     * @param registrationToken The JWT token carrying the current registration
     *                          state/step.
     * @return The generated OTP string.
     * @throws RuntimeException If token is missing, step is invalid, or email is in
     *                          use.
     */
    /**
     * Sends an Email OTP for a new registration flow stateless step, identified by
     * token.
     * 
     * @param email             The email address to verify.
     * @param registrationToken The JWT token carrying the current registration
     *                          state/step.
     * @return An array containing [otpCode, newToken].
     * @throws RuntimeException If token is missing, step is invalid, or email is in
     *                          use.
     */
    @Transactional(noRollbackFor = AuthException.class)
    public String[] sendEmailOtp(String email, String registrationToken) {
        // 1. Validation: registrationToken is mandatory for stateless registration
        if (registrationToken == null || registrationToken.isEmpty()) {
            throw new RuntimeException("Registration token required for new users.");
        }

        // 2. Decode claims from the token to verify the current step
        io.jsonwebtoken.Claims claims = jwtTokenProvider.getClaimsFromRegistrationToken(registrationToken);
        String currentStep = (String) claims.get("step");

        // Only allow email OTP generation if previous step (mobile verification) was
        // successful
        if (!"EMAIL_REQUIRED".equals(currentStep)) {
            throw new RuntimeException("Invalid registration step. Cannot send email OTP.");
        }

        // 3. Validate email format
        String formattedEmail = validateAndFormatEmail(email);

        // 4. Ensure the mobile number is not already fully registered (in case of double registration attempts)
        String mobileNumber = claims.getSubject();
        Optional<User> mobileUser = userRepository.findByMobileNumber(mobileNumber);
        if (mobileUser.isPresent() && !isProfileIncomplete(mobileUser.get())) {
            throw new AuthException(HttpStatus.CONFLICT,
                    "You are already registered. Please go back to the login screen and sign in.");
        }

        // 5. Ensure the email isn't already used by another account
        if (userRepository.findByEmail(formattedEmail).isPresent()) {
            throw new RuntimeException("Email already in use.");
        }

        // 6. Generate the OTP record for this email
        String[] otpResponse = otpService.generateOtp(formattedEmail,
                live.chronogram.auth.enums.OtpType.EMAIL_VERIFICATION);
        String otp = otpResponse[0];
        String sessionId = otpResponse[1];

        // 7. Issue a new Registration Token that now includes the 'email' claim
        String token = jwtTokenProvider.createRegistrationToken(mobileNumber, formattedEmail, "EMAIL_REQUIRED",
                sessionId);

        return new String[] { otp, token, otpResponse[2], otpResponse[3] };
    }

    /**
     * Resends the new device verification OTP to the user's email.
     * 
     * @param temporaryToken The temporary JWT token carrying user context.
     * @return The newly generated OTP code.
     */
    @Transactional(noRollbackFor = AuthException.class)
    public String[] resendNewDeviceOtp(String temporaryToken) {
        // 1. Validation: temporaryToken is required for context
        if (temporaryToken == null || temporaryToken.isEmpty()) {
            throw new RuntimeException("Temporary token is required for resend.");
        }

        // 2. Extract claims and verify the step matches NEW_DEVICE_VERIFICATION
        io.jsonwebtoken.Claims claims = jwtTokenProvider.getClaimsFromRegistrationToken(temporaryToken);
        String step = (String) claims.get("step");
        if (!"DEVICE_APPROVAL_REQUIRED".equals(step)) {
            throw new RuntimeException("Invalid temporary token for new device verification.");
        }

        String email = (String) claims.get("email");
        String sessionTokenId = (String) claims.get("sessionId");

        // 3. Verify OTP Session continuity
        live.chronogram.auth.model.OtpVerification existingOtp = otpVerificationRepository
                .findFirstByTargetAndOtpTypeOrderByCreatedTimestampDesc(email,
                        live.chronogram.auth.enums.OtpType.NEW_DEVICE_VERIFICATION)
                .orElse(null);

        if (existingOtp != null && sessionTokenId != null && !sessionTokenId.equals(existingOtp.getSessionId())) {
            throw new AuthException(HttpStatus.UNAUTHORIZED,
                    "INVALID_OTP_SESSION: The temporary token does not match the current OTP attempt.");
        }

        // 4. Fetch the user associated with this email
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User found in token but not in database"));

        // 4.5 Security Check: Status Blocked/Deleted
        String statusId = user.getUserStatus() != null ? user.getUserStatus().getUserStatusId() : "ACTIVE";
        if ("BLOCK".equals(statusId)) {
            throw new AuthException(HttpStatus.FORBIDDEN,
                    "Account is blocked. Reason: "
                            + (user.getStatusReason() != null ? user.getStatusReason() : "Contact support."));
        }
        if (Boolean.TRUE.equals(user.getIsDeleted())) {
            throw new AuthException(HttpStatus.GONE, "This account has been deleted.");
        }

        // 5. Account Lockout check
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now())) {
            long minutesLeft = java.time.Duration.between(LocalDateTime.now(), user.getLockedUntil())
                    .toMinutes();
            if (minutesLeft == 0)
                minutesLeft = 1;
            throw new AuthException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Account locked. Please try again after " + minutesLeft + " minute(s).");
        }

        // 6. Generate a NEW OTP with lockout/resend tracking
        String[] otpResponse = generateOtpWithLockout(user.getEmail(), OtpType.NEW_DEVICE_VERIFICATION,
                Optional.of(user), true, false);
        String newOtp = otpResponse[0];
        String newSessionId = otpResponse[1];

        // 7. Re-issue a temporary token bound to the NEW session ID
        String mobileNumber = user.getMobileNumber();
        String newToken = jwtTokenProvider.createRegistrationToken(mobileNumber, user.getEmail(),
                "DEVICE_APPROVAL_REQUIRED", newSessionId);

        return new String[] { newOtp, newToken, otpResponse[2], otpResponse[3] };
    }

    /**
     * Resends an Email OTP directly by a registered email address.
     * 
     * @param email The target email.
     * @return The generated OTP string.
     */
    @Transactional(noRollbackFor = AuthException.class)
    public String[] resendEmailOtpByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "User not found with this email."));

        return generateOtpWithLockout(user.getEmail(), OtpType.EMAIL_VERIFICATION, Optional.of(user), true, false);
    }

    /**
     * Entry point to verify OTP specifically for registration (NEW users).
     * Prevents operations if the user is already found in the system.
     * Delegates to the core @Transactional verifyOtpAndLogin.
     */
    @Transactional(noRollbackFor = AuthException.class)
    public TokenResponse verifyOtpForRegistration(String mobileNumber, String otpCode, String otpSessionToken,
            String emailOtpCode,
            String deviceId, String simSerial, String pushToken,
            String deviceName, String deviceModel, String osName, String osVersion, String appVersion,
            Double latitude, Double longitude, String country, String city, String ipAddress, String userAgent) {

        // 1. Sanitize the input mobile number
        String sanitizedMobile = sanitizePhoneNumber(mobileNumber);
        logger.info("Verifying registration OTP for mobile: {}", sanitizedMobile);

        // 2. Check if the user is already fully registered
        Optional<User> existingUserOpt = userRepository.findByMobileNumber(sanitizedMobile);
        if (existingUserOpt.isPresent()) {
            User existingUser = existingUserOpt.get();
            // If they have a name and email, they are already registered and should use
            // Login
            if (existingUser.getEmail() != null && existingUser.getName() != null) {
                throw new AuthException(HttpStatus.CONFLICT,
                        "User already exists. Please login.");
            } else {
                // If the profile is incomplete, we allow them to continue the registration flow
                logger.info("Allowing incomplete user to verify registration OTP: {}", sanitizedMobile);
            }
        }

        // 3. Delegate to the core logic.
        TokenResponse response = verifyOtpAndLogin(sanitizedMobile, otpCode, otpSessionToken, emailOtpCode, false,
                deviceId, simSerial,
                pushToken,
                deviceName, deviceModel, osName, osVersion, appVersion, latitude, longitude, country, city, ipAddress,
                userAgent);

        // Track progress: Step 2 (Mobile OTP verified)
        updateRegistrationProgressForNewUser(sanitizedMobile, "OTP_VERIFIED");

        return response;
    }

    /**
     * Entry point to verify OTP specifically for login (EXISTING users).
     * Stops operation if the user is not found.
     * Delegates to the core @Transactional verifyOtpAndLogin.
     */
    @Transactional(noRollbackFor = AuthException.class)
    public TokenResponse verifyOtpForLogin(String mobileNumber, String otpCode, String otpSessionToken,
            String emailOtpCode,
            boolean isRecoveryFlow,
            String deviceId, String simSerial, String pushToken,
            String deviceName, String deviceModel, String osName, String osVersion, String appVersion,
            Double latitude, Double longitude, String country, String city, String ipAddress, String userAgent) {

        // 1. Sanitize the input mobile number
        String sanitizedMobile = sanitizePhoneNumber(mobileNumber);
        logger.info("Verifying login OTP for mobile: {}", sanitizedMobile);

        // 2. Security Check: User MUST exist for the login flow
        if (userRepository.findByMobileNumber(sanitizedMobile).isEmpty()) {
            throw new AuthException(HttpStatus.NOT_FOUND,
                    "User not found. Please register.");
        }

        // 3. Delegate to the core logic
        return verifyOtpAndLogin(sanitizedMobile, otpCode, otpSessionToken, emailOtpCode, isRecoveryFlow, deviceId,
                simSerial, pushToken,
                deviceName, deviceModel, osName, osVersion, appVersion, latitude, longitude, country, city, ipAddress,
                userAgent);
    }

    /**
     * The core logic for verifying OTP and handling the user login/registration
     * session state.
     * It handles:
     * 1. Mobile OTP verification and rate limiting lockouts.
     * 2. Checking if User exists (returning a stateless RegistrationToken for new
     * users).
     * 3. Verifying and evaluating device trust capabilities (triggers new device
     * approval loops).
     * 4. Issues the final Access/Refresh tokens and logs the history if successful.
     * 
     * @return Either a valid Access Token for logged-in users, or a Registration
     *         Token for the next flow step.
     */
    @Transactional(noRollbackFor = AuthException.class)
    public TokenResponse verifyOtpAndLogin(String mobileNumber, String otpCode, String otpSessionToken,
            String emailOtpCode,
            boolean isRecoveryFlow,
            String deviceId, String simSerial, String pushToken,
            String deviceName, String deviceModel, String osName, String osVersion, String appVersion,
            Double latitude, Double longitude, String country, String city, String ipAddress, String userAgent) {

        // 1. Validate mandatory Device ID
        if (deviceId == null || deviceId.trim().isEmpty()) {
            throw new AuthException(HttpStatus.BAD_REQUEST,
                    "DEVICE_ID_REQUIRED: deviceId is required.");
        }
        final String finalDeviceId = deviceId.trim();

        // 2. Validate mandatory OTP Session Token (continuity proof)
        if (otpSessionToken == null || otpSessionToken.trim().isEmpty()) {
            throw new AuthException(HttpStatus.BAD_REQUEST,
                    "OTP_SESSION_TOKEN_REQUIRED: The OtpSessionToken from the send-otp step is missing.");
        }

        // Parse claims from the OTP session token
        io.jsonwebtoken.Claims sessionClaims = jwtTokenProvider.getClaimsFromOtpSessionToken(otpSessionToken);
        String sessionMobile = sessionClaims.getSubject();
        String sessionDeviceId = (String) sessionClaims.get("deviceId");

        String sanitizedMobile = sanitizePhoneNumber(mobileNumber);

        // Security Validation: Ensure the phone and device match what was used when
        // requesting the OTP
        if (!sanitizedMobile.equals(sessionMobile)) {
            throw new AuthException(HttpStatus.FORBIDDEN,
                    "The session has expired or belongs to a different mobile number. Please request a new OTP.");
        }
        if (!deviceId.equals(sessionDeviceId)) {
            throw new AuthException(HttpStatus.FORBIDDEN,
                    "Cross-device OTP verification attempt detected and blocked.");
        }

        String sessionTokenId = (String) sessionClaims.get("sessionId");

        // 3. Locate the Mobile OTP record in the database
        live.chronogram.auth.model.OtpVerification mobileOtp = otpVerificationRepository
                .findFirstByTargetAndOtpTypeOrderByCreatedTimestampDesc(sanitizedMobile,
                        live.chronogram.auth.enums.OtpType.MOBILE_LOGIN)
                .orElseThrow(() -> new AuthException(
                        HttpStatus.BAD_REQUEST,
                        "OTP not found or expired. Please request a new one."));

        // Validate that the session ID matches (prevents token reuse after a new OTP is
        // sent)
        if (sessionTokenId == null || !sessionTokenId.equals(mobileOtp.getSessionId())) {
            throw new AuthException(HttpStatus.UNAUTHORIZED,
                    "Invalid session: The session token has expired or been replaced. Please request a new OTP.");
        }

        // 4. Verify the actual OTP code via OtpService
        boolean isMobileVerified = false;
        try {
            isMobileVerified = otpService.verifyOtp(sanitizedMobile, OtpType.MOBILE_LOGIN, otpCode);
        } catch (AuthException e) {
            // Special Case: Handle too many failed attempts
            if (e.getStatus() == HttpStatus.TOO_MANY_REQUESTS) {
                Optional<User> userOpt = userRepository.findByMobileNumber(sanitizedMobile);
                if (userOpt.isPresent()) {
                    User userToBlock = userOpt.get();
                    // Block the account temporarily for 15 minutes
                    Optional<live.chronogram.auth.model.UserStatus> blockedStatus = userStatusRepository
                            .findById("BLOCK");
                    blockedStatus.ifPresent(status -> {
                        userToBlock.setUserStatus(status);
                        userToBlock.setStatusReason("Exceeded maximum OTP attempts.");
                        userToBlock.setLockedUntil(LocalDateTime.now().plusMinutes(lockDurationMinutes));
                        userRepository.save(userToBlock);

                        // Audit Log for the failure
                        saveLoginHistory(userToBlock, null, ipAddress, userAgent, false, "EXCEEDED_MAX_ATTEMPTS",
                                latitude, longitude, city, country);
                    });
                }
            }
            throw e;
        }

        // If verification failed (but limit not reached), log and throw
        if (!isMobileVerified) {
            Optional<User> userOpt = userRepository.findByMobileNumber(sanitizedMobile);
            userOpt.ifPresent(u -> saveLoginHistory(u, null, ipAddress, userAgent, false, "INVALID_OTP", latitude,
                    longitude, city, country));
            throw new AuthException(HttpStatus.UNAUTHORIZED,
                    "Incorrect OTP. Please check the code and try again.");
        }

        // 2. Find or Create User
        Optional<User> userOpt = userRepository.findByMobileNumber(sanitizedMobile);

        if (userOpt.isEmpty()) {
            // CASE: COMPLETELY NEW USER
            // Logic: We don't create a DB record yet to keep the system stateless and
            // prevent spam registrations.
            // Instead, we return a stateless Registration Token with the claim
            // 'EMAIL_REQUIRED'.

            // STRICT SIM CHECK: We enforce that a physical SIM serial is provided during
            // registration for security.
            if (simSerial == null || simSerial.trim().isEmpty()) {
                throw new RuntimeException("SIM_REQUIRED: Registration requires a valid SIM card.");
            }

            // Capture the session ID from the verified Mobile OTP to maintain continuity in
            // the next token
            String sessionId = mobileOtp.getSessionId();

            // Track progress: Step 1 (Mobile OTP verified)
            saveIncompleteRegistration(sanitizedMobile, "MOBILE_VERIFIED", null, null,
                    deviceId, deviceName, deviceModel, osName, osVersion, ipAddress, latitude, longitude, city,
                    country);

            return new TokenResponse(
                    jwtTokenProvider.createRegistrationToken(sanitizedMobile, null, "EMAIL_REQUIRED", sessionId),
                    null, "Mobile verified. Verify Email to proceed.");
        }

        User user = userOpt.get();

        // Check if the user is marked as deleted
        if (Boolean.TRUE.equals(user.getIsDeleted())) {
            throw new AuthException(HttpStatus.GONE,
                    "This account has been deleted.");
        }

        // Check if the user is explicitly blocked by an admin
        if (Boolean.TRUE.equals(user.getIsBlocked())) {
            throw new AuthException(HttpStatus.FORBIDDEN,
                    "USER_BLOCKED: Your account has been blocked by an administrator.");
        }

        // Check Admin Approval Status (Non-blocking for now to satisfy existing APK
        // logic)
        if (Boolean.FALSE.equals(user.getIsDeleted()) && Boolean.FALSE.equals(user.getIsBlocked())) {
            if ("REJECTED".equals(user.getApprovalStatus())) {
                throw new AuthException(HttpStatus.FORBIDDEN,
                        "USER_REJECTED: Your account access has been rejected by an administrator.");
            }
            // We allow PENDING or null for now so APK doesn't break
        }

        // Check Registration Completion Status (Non-blocking for now)
        // This allows the admin panel to track progress without forcing the user out of
        // the app.

        // CASE: INCOMPLETE USER PROFILE (e.g., deleted email/name or crashed last time)
        // Logic: Redirect them back to the Email Verification step.
        if (user.getEmail() == null || user.getName() == null) {
            logger.info("Found incomplete user profile for mobile: {}. Returning RegistrationToken.", sanitizedMobile);

            if (simSerial == null || simSerial.trim().isEmpty()) {
                throw new RuntimeException("SIM_REQUIRED: Registration requires a valid SIM card.");
            }

            String sessionId = mobileOtp.getSessionId();

            return new TokenResponse(
                    jwtTokenProvider.createRegistrationToken(sanitizedMobile, null, "EMAIL_REQUIRED", sessionId),
                    null, "Email verification required to complete registration.");
        }

        // 3. Check Account Status
        if (!"ACTIVE".equals(user.getUserStatus().getUserStatusId())
                && !"BLOCK".equals(user.getUserStatus().getUserStatusId())) {
            throw new RuntimeException("Account is " + user.getUserStatus().getName());
        }

        if (user.getLockedUntil() != null) {
            if (user.getLockedUntil().isAfter(LocalDateTime.now(java.time.ZoneOffset.UTC))) {
                long minutesLeft = java.time.Duration.between(LocalDateTime.now(java.time.ZoneOffset.UTC), user.getLockedUntil())
                        .toMinutes();
                if (minutesLeft == 0)
                    minutesLeft = 1;
                throw new AuthException(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "Account locked. Please try again after " + minutesLeft + " minute(s).");
            } else {
                // Lock expired naturally, clear it
                user.setLockedUntil(null);
                userRepository.save(user);
            }
        }

        // 4. Device Security & Trust Logic
        // Determine if this specific hardware ID (deviceId) has been previously
        // verified for this user.
        boolean isDeviceTrusted = deviceService.isDeviceTrusted(user, finalDeviceId);
        boolean hasOtherTrustedDevices = deviceService.hasAnyTrustedDevice(user.getUserId());
        boolean shouldTrustThisDevice = false;

        // Fetch all registered devices to determine where to send approval requests
        List<UserDevice> allUserDevices = userDeviceRepository.findByUser_UserId(user.getUserId());

        if (isDeviceTrusted) {
            // OPTION A: Device is already recognized and trusted. Direct Login allowed.
            shouldTrustThisDevice = true;
        } else {
            // OPTION B: This is a NEW or UNTRUSTED Device
            if (!hasOtherTrustedDevices) {
                // Scenario: User has NO other trusted devices (e.g., first login on a new
                // account
                // or they cleared all devices). We trust the current device implicitly
                // since they just passed the primary Mobile OTP challenge.
                shouldTrustThisDevice = true;
            } else {
                // Scenario: Secondary Device Login attempt.
                if (isRecoveryFlow) {
                    // LOST DEVICE / RECOVERY FLOW: Must verify via secondary Email OTP immediately
                    if (emailOtpCode == null || emailOtpCode.isEmpty()) {
                        throw new RuntimeException("Email OTP required for recovery flow.");
                    }
                    boolean isEmailVerified = otpService.verifyOtp(user.getEmail(), OtpType.EMAIL_VERIFICATION,
                            emailOtpCode);
                    if (!isEmailVerified) {
                        throw new RuntimeException("Invalid Email OTP");
                    }
                    shouldTrustThisDevice = true;
                } else {
                    // STANDARD NEW DEVICE FLOW: Requires explicit approval from other devices OR
                    // Email OTP

                    // Check if the user already provided an Email OTP in the login payload
                    if (emailOtpCode != null && !emailOtpCode.isEmpty()) {
                        boolean isEmailVerified = otpService.verifyOtp(user.getEmail(), OtpType.NEW_DEVICE_VERIFICATION,
                                emailOtpCode);
                        if (isEmailVerified) {
                            shouldTrustThisDevice = true;
                        } else {
                            throw new RuntimeException("Invalid Email OTP");
                        }
                    }

                    if (!shouldTrustThisDevice) {
                        // APPROVAL REQUIRED: Block the login and send approval requests to trusted
                        // devices
                        List<UserDevice> trustedDevices = allUserDevices.stream()
                                .filter(d -> Boolean.TRUE.equals(d.getIsTrusted()))
                                .toList();

                        for (UserDevice td : trustedDevices) {
                            String safeDeviceName = (deviceName != null && !deviceName.isEmpty()) ? deviceName
                                    : "Unknown Device";
                            String safeDeviceModel = (deviceModel != null && !deviceModel.isEmpty()) ? deviceModel
                                    : "Unknown Model";

                            // Send push notification to existing trusted devices
                            notificationService.sendNotification(td.getPushToken(),
                                    "New Login Attempt",
                                    "Attempt details: " + safeDeviceName + " (" + safeDeviceModel + ")",
                                    "LOGIN_APPROVAL_REQUEST",
                                    "{\"targetDeviceId\": \"" + finalDeviceId + "\"}");
                        }

                        // Also send a verification OTP to the user's primary email address
                        if (user.getEmail() != null && !user.getEmail().isEmpty()) {
                            String[] otpResponse = otpService.generateOtp(user.getEmail(),
                                    live.chronogram.auth.enums.OtpType.NEW_DEVICE_VERIFICATION);
                            String sessionId = otpResponse[1];

                            // Provide a masked email hint to the user for privacy (e.g., ad***@gmail.com)
                            String email = user.getEmail();
                            String maskedEmail = maskEmail(email);

                            // Issue a temporary JWT that the user must use to verify the Email OTP
                            String temporaryToken = jwtTokenProvider.createRegistrationToken(sanitizedMobile,
                                    user.getEmail(), "DEVICE_APPROVAL_REQUIRED", sessionId);

                            // Halt the login flow and return 401-subset status via specialized exception
                            throw new live.chronogram.auth.exception.DeviceApprovalRequiredException(
                                    "APPROVAL_REQUIRED: OTP sent to registered email.", maskedEmail, temporaryToken);
                        } else {
                            // If no email is linked, the user is stuck unless they have other devices.
                            throw new live.chronogram.auth.exception.EmailLinkingRequiredException(
                                    "EMAIL_REQUIRED: No email registered. Cannot verify new device.");
                        }
                    }
                }
            }
        }

        return completeLoginAndIssueTokens(user, finalDeviceId, simSerial, pushToken, deviceName, deviceModel,
                osName, osVersion, appVersion, latitude, longitude, country, city, ipAddress, userAgent,
                shouldTrustThisDevice);
    }

    /**
     * Shared logic for completing a successful login, registering/updating the
     * device,
     * creating a session, and issuing JWT tokens.
     */
    private TokenResponse completeLoginAndIssueTokens(User user, String deviceId, String simSerial, String pushToken,
            String deviceName, String deviceModel, String osName, String osVersion, String appVersion,
            Double latitude, Double longitude, String country, String city, String ipAddress, String userAgent,
            boolean trustThisDevice) {

        // 1. Notify other trusted devices of the new login
        List<UserDevice> allUserDevices = userDeviceRepository.findByUser_UserId(user.getUserId());
        if (trustThisDevice) {
            List<UserDevice> otherTrustedDevices = allUserDevices.stream()
                    .filter(d -> Boolean.TRUE.equals(d.getIsTrusted()) && !d.getDeviceId().equals(deviceId))
                    .toList();

            for (UserDevice td : otherTrustedDevices) {
                String loc = (latitude != null && longitude != null) ? (latitude + "," + longitude)
                        : "Unknown Location";
                notificationService.sendLoginAlert(td.getPushToken(), deviceName, loc);
            }
        }

        // 2. Register or Update Device metadata
        UserDevice device = deviceService.registerOrUpdateDevice(user, deviceId, simSerial, pushToken, deviceName,
                deviceModel, osName, osVersion, appVersion, latitude, longitude, country, city, trustThisDevice);

        // Update registration status if in flow
        if ("PROFILE_CREATED".equals(user.getRegistrationStatus())) {
            user.setRegistrationStatus("DEVICE_REGISTERED");
            updateRegistrationProgress(user.getUserId(), "DEVICE_REGISTERED");
        }

        // Update last login
        user.setLastLoginAt(LocalDateTime.now(java.time.ZoneOffset.UTC));
        userRepository.save(user);

        // 3. Issue Security Tokens
        String accessToken = jwtTokenProvider.createAccessToken(user.getUserId(), "USER", device.getUserDeviceId());
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getUserId());

        // 4. Create and persist User Session
        UserSession session = new UserSession();
        session.setUser(user);
        session.setUserDevice(device);
        session.setRefreshTokenHash(hashRefreshToken(refreshToken));
        session.setExpiresTimestamp(LocalDateTime.now(java.time.ZoneOffset.UTC).plusNanos(refreshTokenValidityInMs * 1000000));
        session.setIsRevoked(false);
        session.setIpAddress(ipAddress);
        session.setLatitude(latitude);
        session.setLongitude(longitude);
        session.setCountry(country);
        session.setCity(city);
        userSessionRepository.save(session);

        // 5. Reset Blocked/Banned status or Transition NEW status on successful
        // verified login
        String statusId = user.getUserStatus() != null ? user.getUserStatus().getUserStatusId() : "ACTIVE";
        if ("BLOCK".equals(statusId) || "NEW".equals(statusId)) {
            userStatusRepository.findById("ACTIVE").ifPresent(status -> {
                user.setUserStatus(status);
                user.setLockedUntil(null);
                user.setStatusReason(null);
                userRepository.save(user);
                logger.info("Account status transitioned to ACTIVE after successful login for user: {}",
                        user.getUserId());
            });
        }

        // 6. Audit Trail
        saveLoginHistory(user, device, ipAddress, userAgent, true, null, latitude, longitude, city, country);

        MDC.put("txId", "TX" + System.currentTimeMillis());
        flowLogger.info("event=USER_LOGIN name={} userId={} city={} country={}", user.getName(), user.getUserId(), city,
                country);
        MDC.clear();

        return new TokenResponse(accessToken, refreshToken, "Login successful.");
    }

    /**
     * Rotates or issues a new Access Token given a valid Refresh Token.
     * 
     * @param refreshToken The active refresh token hash.
     * @return A newly generated JWT access token.
     * @throws RuntimeException If the token is invalid, missing, revoked, or
     *                          expired.
     */
    @Transactional
    public String refreshToken(String refreshToken) {
        // 1. Initial signature and expiration check via JwtTokenProvider
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new AuthException(
                    HttpStatus.UNAUTHORIZED, "Invalid refresh token.");
        }

        // 2. Decode the token to identify the user and their role
        Long userId = jwtTokenProvider.getUserIdFromToken(refreshToken);
        String role = jwtTokenProvider.getRoleFromToken(refreshToken);

        // 3. Security Check: Verify User Status is still Active (Mandatory for
        // Perpetual Sessions)
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException(
                        HttpStatus.UNAUTHORIZED, "User not found."));

        if (!"ACTIVE".equals(user.getUserStatus().getUserStatusId())) {
            throw new AuthException(
                    HttpStatus.FORBIDDEN, "Account is " + user.getUserStatus().getName() + ". Cannot renew session.");
        }

        // 4. Revocation Check: Search for an ACTIVE database session that matches this
        // token.
        // Since we store tokens as SHA-256 hashes, we must hash the incoming token and
        // compare.
        List<UserSession> activeSessions = userSessionRepository.findByUser_UserId(userId).stream()
                .filter(s -> !s.getIsRevoked() && s.getExpiresTimestamp().isAfter(LocalDateTime.now(java.time.ZoneOffset.UTC)))
                .toList();

        UserSession matchedSession = activeSessions.stream()
                .filter(s -> hashRefreshToken(refreshToken).equals(s.getRefreshTokenHash()))
                .findFirst()
                .orElseThrow(() -> new AuthException(
                        HttpStatus.UNAUTHORIZED, "Invalid or expired refresh token."));

        // 5. Generate a NEW Access Token
        return jwtTokenProvider.createAccessToken(userId, role, matchedSession.getUserDevice().getUserDeviceId());
    }

    /**
     * Internal session validation for microservices.
     */
    @Transactional(readOnly = true)
    public void validateSession(String token) {
        // 1. Signature & Expiry check
        if (!jwtTokenProvider.validateToken(token)) {
            throw new AuthException(HttpStatus.UNAUTHORIZED,
                    "Invalid or Expired Token");
        }

        // 2. Revocation Check (User existence + Status + Session Active)
        Long userId = jwtTokenProvider.getUserIdFromToken(token);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException(
                        HttpStatus.UNAUTHORIZED, "User not found."));

        if (!"ACTIVE".equals(user.getUserStatus().getUserStatusId())) {
            throw new AuthException(
                    HttpStatus.UNAUTHORIZED, "Account is " + user.getUserStatus().getName());
        }

        boolean hasActiveSession = userSessionRepository.existsByUser_UserIdAndIsRevoked(userId, false);
        if (!hasActiveSession) {
            throw new AuthException(HttpStatus.UNAUTHORIZED,
                    "Session has been revoked or logged out.");
        }
    }

    /**
     * Revokes a user session, effectively logging out the user.
     * 
     * @param refreshToken The raw refresh token string.
     */
    @Transactional
    public void logout(String refreshToken) {
        // 1. Identify the user from the refresh token
        Long userId = jwtTokenProvider.getUserIdFromToken(refreshToken);

        // 2. Locate the specific session in the database
        List<UserSession> activeSessions = userSessionRepository.findByUser_UserId(userId).stream()
                .filter(s -> !s.getIsRevoked())
                .toList();

        UserSession matchedSession = activeSessions.stream()
                .filter(s -> hashRefreshToken(refreshToken).equals(s.getRefreshTokenHash()))
                .findFirst()
                .orElseThrow(() -> new AuthException(
                        HttpStatus.UNAUTHORIZED, "Invalid refresh token."));

        // 3. Mark the session as revoked (invalidating the refresh token immediately)
        matchedSession.setIsRevoked(true);
        userSessionRepository.save(matchedSession);

        // 4. Audit Log the logout event
        String txId = "TX" + System.currentTimeMillis();
        MDC.put("txId", txId);
        flowLogger.info("event=USER_LOGOUT name={} userId={} city={} country={}", matchedSession.getUser().getName(),
                userId, matchedSession.getCity(), matchedSession.getCountry());
        MDC.clear();
    }

    /**
     * Handles the flow when a user validates an Email OTP strictly prompted by a
     * New Device.
     * Marks the device as trusted upon success and generates tokens.
     * 
     * @return The final Access Token granting access from the new device.
     */
    @Transactional(noRollbackFor = AuthException.class)
    public TokenResponse verifyNewDevice(VerifyNewDeviceRequest request, String ipAddress,
            String userAgent) {

        // 1. Mandatory Device ID Validation (Trimmed for accuracy)
        String finalDeviceId = request.getDeviceId() != null ? request.getDeviceId().trim() : null;
        if (finalDeviceId == null || finalDeviceId.isEmpty()) {
            throw new AuthException(
                    HttpStatus.BAD_REQUEST,
                    "Device ID is strictly required for new device verification.");
        }

        // 2. Normalize and identify user context
        String sanitizedMobile;
        String emailToVerify;
        String sessionTokenId = null;

        if (request.getTemporaryToken() != null && !request.getTemporaryToken().trim().isEmpty()) {
            // HIGH SECURITY FLOW (Token-based): Identify user and session via JWT
            io.jsonwebtoken.Claims claims = jwtTokenProvider
                    .getClaimsFromRegistrationToken(request.getTemporaryToken());

            String step = (String) claims.get("step");
            if (!"DEVICE_APPROVAL_REQUIRED".equals(step)) {
                throw new AuthException(HttpStatus.UNAUTHORIZED,
                        "Invalid temporary token for new device verification.");
            }

            sanitizedMobile = claims.getSubject();
            emailToVerify = (String) claims.get("email");
            sessionTokenId = (String) claims.get("sessionId");

            logger.info("Verifying new device via token for mobile: {} and email: {}", sanitizedMobile, emailToVerify);
        } else {
            // LEGACY FLOW (Mobile-based): Identify user via provided mobile number
            sanitizedMobile = sanitizePhoneNumber(request.getMobileNumber());

            User userHint = userRepository.findByMobileNumber(sanitizedMobile)
                    .orElseThrow(() -> new live.chronogram.auth.exception.AuthException(
                            org.springframework.http.HttpStatus.NOT_FOUND, "User not found"));
            emailToVerify = userHint.getEmail();
        }

        // 3. Fetch user and perform state checks
        User user = userRepository.findByMobileNumber(sanitizedMobile)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // 3.2 Security Check: Status Blocked/Deleted
        String statusId = user.getUserStatus() != null ? user.getUserStatus().getUserStatusId() : "ACTIVE";
        if ("BLOCK".equals(statusId)) {
            throw new AuthException(HttpStatus.FORBIDDEN,
                    "Account is blocked. Reason: "
                            + (user.getStatusReason() != null ? user.getStatusReason() : "Contact support."));
        }
        if (Boolean.TRUE.equals(user.getIsDeleted())) {
            throw new AuthException(HttpStatus.GONE, "This account has been deleted.");
        }

        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now())) {
            long minutesLeft = java.time.Duration.between(LocalDateTime.now(), user.getLockedUntil())
                    .toMinutes();
            if (minutesLeft == 0)
                minutesLeft = 1;
            throw new AuthException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Account locked. Please try again after " + minutesLeft + " minute(s).");
        }

        // Ensure email matches user profile
        if (emailToVerify == null || !emailToVerify.equalsIgnoreCase(user.getEmail())) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "Email mismatch for new device verification.");
        }

        // 4. Verify Session Continuity and OTP
        String processedEmail = emailToVerify != null ? emailToVerify.trim() : null;
        live.chronogram.auth.model.OtpVerification existingOtp = otpVerificationRepository
                .findFirstByTargetAndOtpTypeOrderByCreatedTimestampDesc(processedEmail,
                        live.chronogram.auth.enums.OtpType.NEW_DEVICE_VERIFICATION)
                .orElseThrow(
                        () -> new AuthException(HttpStatus.BAD_REQUEST,
                                "Invalid or expired session for new device verification."));

        // sessionId Check (Only if token was provided)
        if (sessionTokenId != null && !sessionTokenId.equals(existingOtp.getSessionId())) {
            throw new AuthException(HttpStatus.UNAUTHORIZED,
                    "INVALID_OTP_SESSION: The temporary token does not match the current OTP attempt.");
        }

        boolean isVerified = false;
        try {
            isVerified = otpService.verifyOtp(emailToVerify,
                    live.chronogram.auth.enums.OtpType.NEW_DEVICE_VERIFICATION, request.getOtp());
        } catch (AuthException e) {
            // Lock account on too many failed attempts
            if (e.getStatus() == HttpStatus.TOO_MANY_REQUESTS) {
                Optional<live.chronogram.auth.model.UserStatus> blockedStatus = userStatusRepository.findById("BLOCK");
                blockedStatus.ifPresent(status -> {
                    user.setUserStatus(status);
                    user.setStatusReason("Exceeded maximum OTP attempts during new device verification.");
                    user.setLockedUntil(LocalDateTime.now(java.time.ZoneOffset.UTC).plusMinutes(lockDurationMinutes));
                    userRepository.save(user);
                });
            }
            throw e;
        }
        if (!isVerified) {
            throw new AuthException(HttpStatus.UNAUTHORIZED,
                    "Invalid OTP for new device verification");
        }

        // 4. Success: Register and permanently Mark the Device as Trusted
        UserDevice device = deviceService.registerOrUpdateDevice(user,
                finalDeviceId,
                null, // simSerial may not be present in this callback
                null, // pushToken may not be present in this callback
                request.getDeviceName(),
                request.getDeviceModel(),
                request.getOsName(),
                request.getOsVersion(),
                request.getAppVersion(),
                request.getLatitude(),
                request.getLongitude(),
                request.getCountry(),
                request.getCity(),
                true); // TRUST GRANTED

        // 5. Create final Session & issue Tokens
        String accessToken = jwtTokenProvider.createAccessToken(user.getUserId(), "USER", device.getUserDeviceId());
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getUserId());

        UserSession session = new UserSession();
        session.setUser(user);
        session.setUserDevice(device);
        session.setRefreshTokenHash(hashRefreshToken(refreshToken));
        session.setExpiresTimestamp(LocalDateTime.now(java.time.ZoneOffset.UTC).plusNanos(refreshTokenValidityInMs * 1000000));
        session.setIsRevoked(false);
        session.setIpAddress(ipAddress);
        session.setLatitude(request.getLatitude());
        session.setLongitude(request.getLongitude());
        session.setCountry(request.getCountry());
        session.setCity(request.getCity());
        userSessionRepository.save(session);

        // 6. Security: Notify other trusted devices of the new verified login
        List<UserDevice> allUserDevices = userDeviceRepository.findByUser_UserId(user.getUserId());
        List<UserDevice> otherTrustedDevices = allUserDevices.stream()
                .filter(d -> Boolean.TRUE.equals(d.getIsTrusted()) && !d.getDeviceId().equals(finalDeviceId))
                .toList();

        for (UserDevice td : otherTrustedDevices) {
            String loc = (request.getLatitude() != null && request.getLongitude() != null)
                    ? (request.getLatitude() + "," + request.getLongitude())
                    : "Unknown Location";
            notificationService.sendLoginAlert(td.getPushToken(), request.getDeviceName(), loc);
        }

        // 7. Audit Logging
        saveLoginHistory(user, device, ipAddress, userAgent, true, null, request.getLatitude(), request.getLongitude(),
                request.getCity(), request.getCountry());

        return new TokenResponse(accessToken, refreshToken, "New device verified and logged in.");
    }

    @Transactional(noRollbackFor = AuthException.class)
    public String[] linkEmail(live.chronogram.auth.dto.LinkEmailRequest request) {
        // 1. Security Check: A valid registration token is mandatory to prove the user
        // has already verified their mobile
        if (request.getRegistrationToken() == null || request.getRegistrationToken().trim().isEmpty()) {
            throw new RuntimeException("Registration token is required to link an email securely.");
        }

        // 2. Decode the token and verify the current state
        io.jsonwebtoken.Claims claims = jwtTokenProvider
                .getClaimsFromRegistrationToken(request.getRegistrationToken());

        String currentStep = (String) claims.get("step");
        if (!"EMAIL_REQUIRED".equals(currentStep)) {
            throw new RuntimeException("Invalid registration step. Cannot link email.");
        }

        String mobileNumber = claims.getSubject();

        // 3. Prevent linking to an already fully registered account via this flow
        if (userRepository.findByMobileNumber(mobileNumber).isPresent()) {
            User existingUser = userRepository.findByMobileNumber(mobileNumber).get();
            if (!isProfileIncomplete(existingUser)) {
                throw new AuthException(HttpStatus.CONFLICT,
                        "ACCOUNT_EXISTS: Your mobile is already linked to a complete account. Please log in.");
            }
        }

        // 4. Validate and format the target email address
        String formattedEmail = validateAndFormatEmail(request.getEmail());

        // 5. Global Uniqueness Check: Ensure no other user is using this email
        if (userRepository.findByEmail(formattedEmail).isPresent()) {
            throw new RuntimeException("Email already in use.");
        }

        // 6. Generate the OTP record for email linking
        String[] otpResponse = otpService.generateOtp(formattedEmail, live.chronogram.auth.enums.OtpType.EMAIL_LINKING);
        String otp = otpResponse[0];
        String sessionId = otpResponse[1];

        // Track progress: Step 2 (Email link OTP sent)
        saveIncompleteRegistration(mobileNumber, "EMAIL_SENT", formattedEmail, null,
                null, null, null, null, null, null, null, null, null, null);

        // 7. Update the Registration Token to bind the target email and the new session
        // ID
        String token = jwtTokenProvider.createRegistrationToken(mobileNumber, formattedEmail, "EMAIL_REQUIRED",
                sessionId);

        return new String[] { otp, token, otpResponse[2], otpResponse[3] };
    }

    @Transactional(noRollbackFor = AuthException.class)
    public String verifyLinkEmail(live.chronogram.auth.dto.VerifyEmailRequest request) {
        // 1. Mandatory Token Check
        if (request.getRegistrationToken() == null || request.getRegistrationToken().trim().isEmpty()) {
            throw new RuntimeException("Registration token is required to verify email link.");
        }

        // 2. Decode claims and verify step
        io.jsonwebtoken.Claims claims = jwtTokenProvider
                .getClaimsFromRegistrationToken(request.getRegistrationToken());

        String currentStep = (String) claims.get("step");
        if (!"EMAIL_REQUIRED".equals(currentStep)) {
            throw new RuntimeException("Invalid registration step.");
        }

        String mobileNumber = claims.getSubject();
        String boundEmail = (String) claims.get("email");
        String formattedEmail = validateAndFormatEmail(request.getEmail());

        // 3. Security Check: The email being verified MUST match the one bound in the
        // token
        if (boundEmail == null || !boundEmail.equals(formattedEmail)) {
            throw new RuntimeException("Email mismatch. The email being verified does not match the token.");
        }

        String sessionTokenId = (String) claims.get("sessionId");

        // 4. Locate the specific OTP verification record
        live.chronogram.auth.model.OtpVerification emailOtp = otpVerificationRepository
                .findFirstByTargetAndOtpTypeOrderByCreatedTimestampDesc(formattedEmail,
                        live.chronogram.auth.enums.OtpType.EMAIL_LINKING)
                .orElseThrow(() -> new AuthException(
                        HttpStatus.BAD_REQUEST, "OTP_NOT_FOUND: No active OTP record."));

        // 5. Verify session continuity
        if (sessionTokenId == null || !sessionTokenId.equals(emailOtp.getSessionId())) {
            throw new AuthException(HttpStatus.UNAUTHORIZED,
                    "INVALID_OTP_SESSION: The session token does not match the current OTP attempt.");
        }

        // 6. Verify the OTP code
        boolean isVerified = false;
        try {
            isVerified = otpService.verifyOtp(formattedEmail, live.chronogram.auth.enums.OtpType.EMAIL_LINKING,
                    request.getOtp());
        } catch (AuthException e) {
            // Lock the user if they exceed attempts
            if (e.getStatus() == HttpStatus.TOO_MANY_REQUESTS) {
                userRepository.findByMobileNumber(mobileNumber).ifPresent(userToBlock -> {
                    userStatusRepository.findById("BLOCK").ifPresent(status -> {
                        userToBlock.setUserStatus(status);
                        userRepository.save(userToBlock);
                    });
                });
            }
            throw e;
        }

        if (!isVerified) {
            throw new AuthException(HttpStatus.UNAUTHORIZED,
                    "Invalid Email OTP");
        }

        // Track progress: Step 3 (Email verified)
        saveIncompleteRegistration(mobileNumber, "EMAIL_VERIFIED", formattedEmail, null,
                null, null, null, null, null, null, null, null, null, null);

        // 7. Success: Return a new token that allows the user to proceed to
        // 'PROFILE_REQUIRED' step
        return jwtTokenProvider.createRegistrationToken(mobileNumber, formattedEmail, "PROFILE_REQUIRED",
                sessionTokenId);
    }

    /**
     * The final step of Registration. Takes user demographic data, validates prior
     * stateless steps
     * by extracting the mobile and email from the valid RegistrationToken, saves
     * the comprehensive
     * User entity, and generates their first session tokens.
     */
    @Transactional
    public TokenResponse completeProfile(live.chronogram.auth.dto.CompleteProfileRequest request, String ipAddress,
            String userAgent) {
        String mobileNumber = null;
        String email = null;

        // 1. Validation: Extract validated info from the Registration Token
        if (request.getRegistrationToken() != null && !request.getRegistrationToken().isEmpty()) {
            io.jsonwebtoken.Claims claims = jwtTokenProvider
                    .getClaimsFromRegistrationToken(request.getRegistrationToken());
            mobileNumber = claims.getSubject();
            email = (String) claims.get("email");

            // Ensure the user has actually passed the previous 'PROFILE_REQUIRED' step
            if (!"PROFILE_REQUIRED".equals(claims.get("step"))) {
                throw new AuthException(HttpStatus.BAD_REQUEST,
                        "Invalid registration step. Please verify email first.");
            }
            logger.info("Completing profile for mobile (from token): {}", mobileNumber);
        } else {
            throw new AuthException(HttpStatus.UNAUTHORIZED,
                    "Security Token Missing: You must complete the OTP verification steps to register.");
        }

        // 2. Identify or Create User record
        Optional<User> existingUserOpt = userRepository.findByMobileNumber(mobileNumber);
        User userToSave;

        if (existingUserOpt.isPresent()) {
            User existingUser = existingUserOpt.get();
            // Safety check: Don't allow re-registration of fully complete profiles
            if (!isProfileIncomplete(existingUser)) {
                throw new AuthException(HttpStatus.CONFLICT,
                        "ACCOUNT_EXISTS: This profile is already complete. Please log in.");
            }
            userToSave = existingUser;
            logger.info("Updating incomplete legacy user profile for mobile: {}", mobileNumber);
        } else {
            userToSave = new User();
            userToSave.setMobileNumber(mobileNumber);
            logger.info("Creating new user profile for mobile: {}", mobileNumber);
        }

        // 3. Update User demographic data
        userToSave.setEmail(email);
        userToSave.setName(request.getName().trim());
        try {
            java.time.LocalDate dateOfBirth = java.time.LocalDate.parse(request.getDob());
            // Logical check for DOB
            if (dateOfBirth.isAfter(java.time.LocalDate.now())) {
                throw new live.chronogram.auth.exception.AuthException(org.springframework.http.HttpStatus.BAD_REQUEST,
                        "Invalid Date of Birth. Cannot be in the future.");
            }
            // Age restriction (12+)
            int age = java.time.Period.between(dateOfBirth, java.time.LocalDate.now()).getYears();
            if (age < 12) {
                throw new live.chronogram.auth.exception.AuthException(org.springframework.http.HttpStatus.BAD_REQUEST,
                        "You must be at least 12 years old to register.");
            }
            userToSave.setDob(dateOfBirth);
        } catch (java.time.format.DateTimeParseException e) {
            throw new live.chronogram.auth.exception.AuthException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Invalid Date of Birth format. Please use YYYY-MM-DD.");
        }

        userToSave.setMobileVerified(true);
        userToSave.setEmailVerified(true);

        // Assign 'APPROVED' approval status by default for new users
        userToSave.setApprovalStatus("APPROVED");
        userToSave.setRegistrationStatus("PROFILE_CREATED");

        // Initialize UserStatus to 'ACTIVE' for new users since admin approval is no longer required
        if (userToSave.getUserStatus() == null) {
            userStatusRepository.findById("ACTIVE").ifPresent(userToSave::setUserStatus);
        }

        User savedUser = userRepository.save(userToSave);

        // Update progress tracking
        updateRegistrationProgress(savedUser.getUserId(), "PROFILE_CREATED");

        // Ensure the device id is present
        String finalDeviceId = request.getDeviceId() != null ? request.getDeviceId().trim() : null;
        if (finalDeviceId == null || finalDeviceId.isEmpty()) {
            throw new live.chronogram.auth.exception.AuthException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Device ID is required to complete profile.");
        }

        String txId = "TX" + System.currentTimeMillis();
        MDC.put("txId", txId);
        flowLogger.info("event=USER_REGISTER name={} userId={} status=APPROVED", savedUser.getName(),
                savedUser.getUserId());
        MDC.clear();

        // 7. Cleanup incomplete registration
        try {
            incompleteRegistrationRepository.deleteByMobileNumber(mobileNumber);
            logger.info("Cleaned up incomplete registration for: {}", mobileNumber);
        } catch (Exception e) {
            logger.warn("Failed to cleanup incomplete registration for {}: {}", mobileNumber, e.getMessage());
        }

        // Return tokens directly for immediate login!
        return completeLoginAndIssueTokens(savedUser, finalDeviceId, request.getSimSerial(), request.getPushToken(),
                request.getDeviceName(), request.getDeviceModel(), request.getOsName(), request.getOsVersion(),
                request.getAppVersion(), request.getLatitude(), request.getLongitude(), request.getCountry(),
                request.getCity(), ipAddress, userAgent, true);
    }

    /**
     * Middle-step in stateless registration: Verifies the email via OTP and
     * progresses the registration token.
     */
    @Transactional(noRollbackFor = AuthException.class)
    public TokenResponse verifyEmailOtpForRegistration(String email, String otpCode, String registrationToken) {
        // 1. Mandatory Token Check
        if (registrationToken == null || registrationToken.isEmpty()) {
            throw new live.chronogram.auth.exception.AuthException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Registration token required.");
        }

        // 2. Decode claims and verify step logic
        io.jsonwebtoken.Claims claims = jwtTokenProvider.getClaimsFromRegistrationToken(registrationToken);
        String mobileNumber = claims.getSubject();
        String currentStep = (String) claims.get("step");

        if (!"EMAIL_REQUIRED".equals(currentStep)) {
            throw new AuthException(HttpStatus.BAD_REQUEST,
                    "Invalid registration step.");
        }

        // 2.5 Security Check: Ensure the mobile number hasn't been registered in the meantime
        Optional<User> mobileUser = userRepository.findByMobileNumber(mobileNumber);
        if (mobileUser.isPresent() && !isProfileIncomplete(mobileUser.get())) {
            throw new AuthException(HttpStatus.CONFLICT,
                    "You are already registered. Please go back to the login screen and sign in.");
        }

        // 3. Email Mismatch Check: If an email is already bound in the token, verify it.
        // If the token email is null, skip strict check (this is the first time we bind it).
        String tokenEmail = (String) claims.get("email");
        if (tokenEmail != null && !tokenEmail.equalsIgnoreCase(email.trim())) {
            throw new AuthException(HttpStatus.BAD_REQUEST,
                    "Email mismatch: The provided email does not match the token's registered email.");
        }

        String formattedEmail = validateAndFormatEmail(email);
        String sessionTokenId = (String) claims.get("sessionId");

        // 4. Fetch the specific OTP record
        live.chronogram.auth.model.OtpVerification emailOtp = otpVerificationRepository
                .findFirstByTargetAndOtpTypeOrderByCreatedTimestampDesc(formattedEmail,
                        live.chronogram.auth.enums.OtpType.EMAIL_VERIFICATION)
                .orElseThrow(() -> new AuthException(
                        HttpStatus.BAD_REQUEST, "OTP_NOT_FOUND: No active OTP record."));

        // 5. Continuity check: Softened for email verification to allow resends/state-drift
        if (sessionTokenId != null && !sessionTokenId.equals(emailOtp.getSessionId())) {
            logger.warn("Session ID mismatch in token ({} vs {}). Proceeding with latest DB record for {}.", 
                sessionTokenId, emailOtp.getSessionId(), formattedEmail);
        }

        // 6. Verify visual code
        boolean isVerified = false;
        try {
            isVerified = otpService.verifyOtp(formattedEmail, OtpType.EMAIL_VERIFICATION, otpCode);
        } catch (AuthException e) {
            // Lock logic handled inside otpService and re-thrown
            throw e;
        }
        if (!isVerified) {
            throw new AuthException(HttpStatus.UNAUTHORIZED,
                    "Invalid Email OTP");
        }

        // 7. Success: Return a token that identifies the user as ready for
        // 'PROFILE_REQUIRED'
        return new TokenResponse(
                jwtTokenProvider.createRegistrationToken(mobileNumber, formattedEmail, "PROFILE_REQUIRED",
                        sessionTokenId),
                null,
                "Email verified. Complete profile to finalize registration.");
    }

    /**
     * Retrieves the profile details for a given user.
     * 
     * @param userId The ID of the user.
     * @return A UserResponse object containing public profile information.
     */
    public live.chronogram.auth.dto.UserResponse getUserDetails(Long userId) {
        // 1. Fetch user data via a projected query (optimizing performance by selecting
        // only necessary fields)
        live.chronogram.auth.dto.UserSummaryProjection user = userRepository.findProjectedByUserId(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // 2. Account Security: Ensure deleted users cannot fetch their details
        if (Boolean.TRUE.equals(user.getIsDeleted())) {
            throw new AuthException(HttpStatus.GONE,
                    "Account deleted.");
        }

        // 2. Security Requirement: Mask PII (Personal Identifiable Information) before
        // returning to the UI
        String maskedEmail = maskEmail(user.getEmail());
        String maskedMobile = maskMobileNumber(user.getMobileNumber());

        // 3. Construct the DTO response
        return new live.chronogram.auth.dto.UserResponse(
                user.getUserId(),
                user.getName(),
                maskedEmail,
                maskedMobile,
                user.getDob() != null ? user.getDob().toString() : null,
                user.getMobileVerified() != null ? user.getMobileVerified() : false,
                user.getEmailVerified() != null ? user.getEmailVerified() : false,
                user.getUserStatus() != null && user.getUserStatus().getName() != null
                        ? user.getUserStatus().getName()
                        : "Unknown");
    }

    /**
     * Masks an email address (e.g., adityaa5@gmail.com -> adit***a5@gmail.com)
     */
    private String maskEmail(String email) {
        if (email == null || email.isEmpty())
            return email;
        int atIndex = email.indexOf("@");
        if (atIndex < 6)
            return email; // Too short to mask with 4+2 pattern

        String namePart = email.substring(0, atIndex);
        String domainPart = email.substring(atIndex);

        String firstPart = namePart.substring(0, 4);
        String lastPart = namePart.substring(namePart.length() - 2);

        return firstPart + "***" + lastPart + domainPart;
    }

    /**
     * Masks a mobile number (e.g., +919366666645 -> +9193******45)
     */
    private String maskMobileNumber(String mobileNumber) {
        if (mobileNumber == null || mobileNumber.length() < 10)
            return mobileNumber;

        // Show first 5 (+91XX) and last 2 (XX)
        String firstPart = mobileNumber.substring(0, 5);
        String lastPart = mobileNumber.substring(mobileNumber.length() - 2);
        String maskedPart = "*".repeat(mobileNumber.length() - 7);

        return firstPart + maskedPart + lastPart;
    }

    /**
     * Checks if a user is already registered with the given mobile number.
     */
    public boolean isUserRegistered(String mobileNumber) {
        // Standardize the input before searching the DB
        String sanitizedMobile = sanitizePhoneNumber(mobileNumber);
        return userRepository.findByMobileNumber(sanitizedMobile).isPresent();
    }

    /**
     * Persists a login attempt (success or failure) to the audit log.
     */
    private void saveLoginHistory(User user, UserDevice device, String ipAddress, String userAgent, boolean success,
            String failureReason, Double latitude, Double longitude, String city, String country) {
        try {
            // 1. Initialize Audit Record
            live.chronogram.auth.model.LoginHistory history = new live.chronogram.auth.model.LoginHistory();
            history.setUserId(user.getUserId());
            history.setIpAddress(ipAddress);
            history.setUserAgent(userAgent);
            history.setSuccess(success);
            history.setFailureReason(failureReason);
            history.setCreatedTimestamp(LocalDateTime.now(java.time.ZoneOffset.UTC));

            // 2. Capture device metadata if available
            if (device != null) {
                history.setDeviceModel(device.getDeviceModel());
                history.setOs(device.getOsName() + " " + device.getOsVersion());
                history.setBrowser(null); // Mobile app context (usually no specific browser)
            }

            // 3. Set Geolocation data
            history.setLatitude(latitude);
            history.setLongitude(longitude);
            history.setCity(city);
            history.setCountry(country);

            // 4. Persist (Safe execution - nested in try-catch)
            loginHistoryRepository.save(history);
        } catch (Exception e) {
            // Security Best Practice: Don't block the actual login flow if the audit trails
            // fail to save
            logger.error("Failed to save login history: {}", e.getMessage());
        }
    }

    /**
     * Helper to check if a user is still in the middle of registration (missing
     * core
     * metadata).
     */
    private boolean isProfileIncomplete(User user) {
        return user.getEmail() == null || user.getEmail().trim().isEmpty() ||
                user.getName() == null || user.getName().trim().isEmpty();
    }

    /**
     * Standardizes and validates phone numbers (e.g., adds country code +91 for
     * India).
     */
    private String sanitizePhoneNumber(String mobileNumber) {
        if (mobileNumber == null) {
            throw new RuntimeException("Mobile number is required");
        }
        // 1. Remove all non-digit formatting characters
        String cleaned = mobileNumber.replaceAll("[\\s\\-\\(\\)]", "");

        // 2. Validate length and format (E.164-ish)
        if (!cleaned.matches("^\\+?\\d{10,15}$")) {
            throw new RuntimeException("Invalid mobile number format.");
        }

        // 3. Auto-fix: If it's 10 digits, assume India (+91)
        if (cleaned.matches("^\\d{10}$")) {
            return "+91" + cleaned;
        }

        // 4. Auto-fix: If it's '91' prefix without central '+'
        if (cleaned.matches("^91\\d{10}$")) {
            return "+" + cleaned;
        }

        return cleaned;
    }

    /**
     * Helper to generate OTP while ensuring account lockout if limits are reached.
     * Returns [otpCode, sessionId]
     */
    private String[] generateOtpWithLockout(String target, OtpType otpType, Optional<User> userOpt) {
        return generateOtpWithLockout(target, otpType, userOpt, false, false);
    }

    /**
     * Helper to generate OTP while ensuring account lockout if limits are reached.
     * When {@code isResend} is {@code true}, bypasses the spam-wait check so a new
     * OTP can be issued immediately even if the previous one has not expired yet.
     *
     * @param target   Phone number or email to generate the OTP for.
     * @param otpType  The OTP context (MOBILE_LOGIN, EMAIL_VERIFICATION, etc.).
     * @param userOpt  Optional user to lock on too-many-requests.
     * @param isResend {@code true} when invoked from a resend endpoint.
     * @return Array of [otpCode, sessionId].
     */
    @Transactional(noRollbackFor = AuthException.class)
    protected String[] generateOtpWithLockout(String target, OtpType otpType, Optional<User> userOpt,
            boolean isResend, boolean skipGeneration) {

        // 1. 🔥 NEW: Auto-detect skipSms mode based on Global Backend Settings & OtpType
        // This ensures 'Resend' flows work even if the Flutter app forgets to pass 'skipSms: true'.
        boolean effectiveSkipGeneration = skipGeneration;
        if (otpType == live.chronogram.auth.enums.OtpType.MOBILE_LOGIN) {
            if (backendMobileOtpEnabled) {
                effectiveSkipGeneration = false; // Forced Testing Mode (Postman/Local)
            } else {
                effectiveSkipGeneration = true;  // Production Mode (Firebase is mandatory)
            }
        } else {
            // For Email, New Device, etc., we ALWAYS generate a real backend OTP.
            effectiveSkipGeneration = false;
        }

        try {
            return otpService.generateOtp(target, otpType, isResend, effectiveSkipGeneration);
        } catch (AuthException e) {
            // We NO LONGER lock the User table for TOO_MANY_REQUESTS during OTP generation.
            // This ensures that if the user is already logged in on a different device,
            // their active session is NOT removed or blocked. The lock exists ONLY at
            // the OtpService/OtpVerification level for the target.
            throw e;
        }
    }

    /**
     * Authenticates a user using a Firebase ID Token.
     * If the user exists, it performs a login.
     * If the user does not exist, it initiates registration.
     */
    @Transactional(noRollbackFor = AuthException.class)
    public TokenResponse authenticateWithFirebase(String idToken, LoginRequest request, String ipAddress,
            String userAgent) {
        // 1. Verify the Firebase Token
        String phoneNumber = firebaseAuthService.verify(idToken);
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            throw new AuthException(
                    HttpStatus.UNAUTHORIZED, "Firebase token did not contain a valid phone number.");
        }

        // 2. Standardize phone number
        String sanitizedMobile = sanitizePhoneNumber(phoneNumber);

        // 2.2 Verify OTP Session Continuity
        // Ensure that the device doing the Firebase login is the exact same device that
        // requested the OTP
        String otpSessionToken = request.getOtpSessionToken();
        if (otpSessionToken == null || otpSessionToken.trim().isEmpty()) {
            throw new AuthException(HttpStatus.BAD_REQUEST,
                    "OTP_SESSION_TOKEN_REQUIRED: The OtpSessionToken from the send-otp step is missing.");
        }

        // Parse claims from the OTP session token (strictly enforce expiration)
        io.jsonwebtoken.Claims sessionClaims = jwtTokenProvider.getClaimsFromOtpSessionToken(otpSessionToken, false);
        String sessionMobile = sessionClaims.getSubject();
        String sessionDeviceId = (String) sessionClaims.get("deviceId");

        if (!sanitizedMobile.equals(sessionMobile)) {
            throw new AuthException(HttpStatus.FORBIDDEN,
                    "OTP session mismatch: This token belongs to a different mobile number or has expired. Please request a new OTP.");
        }
        String requestDeviceId = request.getDeviceId();
        if (requestDeviceId == null || !requestDeviceId.equals(sessionDeviceId)) {
            throw new AuthException(HttpStatus.FORBIDDEN,
                    "Cross-device Firebase login attempt detected and blocked.");
        }

        // Capture the session ID from the token to maintain continuity in potential
        // registration tokens
        String sessionTokenId = (String) sessionClaims.get("sessionId");

        // 2.5 🔥 FIX: Clean up any stale/unverified MOBILE_LOGIN OTP records for this
        // mobile number
        // This prevents the conflict where a prior "skipSms" or manual registration
        // attempt
        // leaves a record in the DB that blocks future requests.
        try {
            otpVerificationRepository.deleteByTargetAndOtpType(sanitizedMobile, OtpType.MOBILE_LOGIN);
            logger.info("Successfully cleaned up stale MySQL OTPs for phone: {}", sanitizedMobile);
        } catch (Exception e) {
            logger.warn("Could not delete stale OTPs during Firebase login (not critical): {}", e.getMessage());
        }

        Optional<User> userOpt = userRepository.findByMobileNumber(sanitizedMobile);

        if (userOpt.isPresent()) {
            User user = userOpt.get();

            // 3. Status Check: Deleted
            if (Boolean.TRUE.equals(user.getIsDeleted())) {
                throw new AuthException(HttpStatus.GONE, "This account has been deleted.");
            }

            // 3.5 🔥 FIX: Handle incomplete profiles for existing users
            // If they have a phone but no name/email, they must proceed with registration
            if (isProfileIncomplete(user)) {
                logger.info("Firebase verification for incomplete user profile: {}. Returning RegistrationToken.",
                        sanitizedMobile);

                // Return a registration token instead of final tokens
                String registrationToken = jwtTokenProvider.createRegistrationToken(sanitizedMobile, null,
                        "EMAIL_REQUIRED", sessionTokenId);
                return new TokenResponse(registrationToken, null,
                        "Email verification required to complete registration.");
            }

            // Status Check: Admin Approval (Blocking)
            if (!"APPROVED".equals(user.getApprovalStatus())) {
                throw new AuthException(HttpStatus.FORBIDDEN,
                        "Your profile is pending admin approval. Please check back later.");
            }

            // Status Check: Blocked or other restricted statuses
            String statusId = user.getUserStatus() != null ? user.getUserStatus().getUserStatusId() : "ACTIVE";
            if ("BLOCK".equals(statusId)) {
                throw new AuthException(HttpStatus.FORBIDDEN,
                        "Account is blocked. Reason: "
                                + (user.getStatusReason() != null ? user.getStatusReason() : "Contact support."));
            }

            if (!"ACTIVE".equals(statusId)) {
                throw new AuthException(HttpStatus.FORBIDDEN,
                        "Account status does not allow login.");
            }

            // 4. Device Security & Trust Logic
            final String finalDeviceId = request.getDeviceId();
            if (finalDeviceId == null || finalDeviceId.trim().isEmpty()) {
                throw new AuthException(HttpStatus.BAD_REQUEST, "DEVICE_ID_REQUIRED: deviceId is required.");
            }

            boolean isDeviceTrusted = deviceService.isDeviceTrusted(user, finalDeviceId);
            boolean hasOtherTrustedDevices = deviceService.hasAnyTrustedDevice(user.getUserId());
            boolean shouldTrustThisDevice = false;

            if (isDeviceTrusted) {
                shouldTrustThisDevice = true;
            } else {
                if (!hasOtherTrustedDevices) {
                    // First login on new device/cleared devices
                    shouldTrustThisDevice = true;
                } else {
                    // Trigger New Device Verification Flow
                    List<UserDevice> allUserDevices = userDeviceRepository.findByUser_UserId(user.getUserId());
                    List<UserDevice> trustedDevices = allUserDevices.stream()
                            .filter(d -> Boolean.TRUE.equals(d.getIsTrusted()))
                            .toList();

                    for (UserDevice td : trustedDevices) {
                        notificationService.sendNotification(td.getPushToken(),
                                "New Login Attempt",
                                "Security Alert: Firebase login from a new device.",
                                "LOGIN_APPROVAL_REQUEST",
                                "{\"targetDeviceId\": \"" + finalDeviceId + "\"}");
                    }

                    if (user.getEmail() != null && !user.getEmail().isEmpty()) {
                        String[] otpResponse = otpService.generateOtp(user.getEmail(), OtpType.NEW_DEVICE_VERIFICATION);
                        String temporaryToken = jwtTokenProvider.createRegistrationToken(sanitizedMobile,
                                user.getEmail(), "DEVICE_APPROVAL_REQUIRED", otpResponse[1]);

                        throw new live.chronogram.auth.exception.DeviceApprovalRequiredException(
                                "APPROVAL_REQUIRED: Security OTP sent to email.", maskEmail(user.getEmail()),
                                temporaryToken);
                    } else {
                        throw new live.chronogram.auth.exception.EmailLinkingRequiredException(
                                "EMAIL_REQUIRED: No email registered for new device verification.");
                    }
                }
            }

            // 5. Handle Login Flow
            logger.info("Firebase login success for user: {}", sanitizedMobile);
            return completeLoginAndIssueTokens(user, finalDeviceId, request.getSimSerial(), request.getPushToken(),
                    request.getDeviceName(), request.getDeviceModel(), request.getOsName(), request.getOsVersion(),
                    request.getAppVersion(), request.getLatitude(), request.getLongitude(), request.getCountry(),
                    request.getCity(), ipAddress, userAgent, shouldTrustThisDevice);
        } else {
            // 5. Handle Registration Flow (Incomplete Profile)
            logger.info("Firebase verification for new/incomplete user: {}", sanitizedMobile);

            // Issue a registration token to proceed with Email verification
            // This ensures Firebase users still follow the full registration flow (Email ->
            // Profile)
            String registrationToken = jwtTokenProvider.createRegistrationToken(sanitizedMobile, null, "EMAIL_REQUIRED",
                    sessionTokenId);

            return new TokenResponse(registrationToken, null,
                    "Firebase verification successful. Please verify your email to proceed.");
        }
    }

    private String validateAndFormatEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            throw new live.chronogram.auth.exception.AuthException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Email is required.");
        }

        String trimmedEmail = email.trim().toLowerCase();

        if (trimmedEmail.length() > 254) {
            throw new live.chronogram.auth.exception.AuthException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Invalid email format (too long).");
        }

        // Stronger email regex: restricts special characters and ensures standard
        // domains.
        String emailRegex = "^[A-Za-z0-9._%+-]{1,64}@[A-Za-z0-9.-]+\\.[A-Za-z]{2,20}$";
        if (!trimmedEmail.matches(emailRegex)) {
            throw new live.chronogram.auth.exception.AuthException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Invalid email format.");
        }

        return trimmedEmail;
    }

    /**
     * Helper to track incomplete registration progress.
     */
    private void saveIncompleteRegistration(String mobileNumber, String step, String email, String name,
            String deviceId, String deviceName, String deviceModel, String osName, String osVersion, String ipAddress,
            Double latitude, Double longitude, String city, String country) {
        try {
            live.chronogram.auth.model.IncompleteRegistration reg = incompleteRegistrationRepository
                    .findByMobileNumber(mobileNumber)
                    .orElse(new live.chronogram.auth.model.IncompleteRegistration());

            reg.setMobileNumber(mobileNumber);
            reg.setLastStep(step);
            if (email != null)
                reg.setEmail(email);
            if (name != null)
                reg.setName(name);

            if (deviceId != null)
                reg.setDeviceId(deviceId);
            if (deviceName != null)
                reg.setDeviceName(deviceName);
            if (deviceModel != null)
                reg.setDeviceModel(deviceModel);
            if (osName != null)
                reg.setOsName(osName);
            if (osVersion != null)
                reg.setOsVersion(osVersion);
            if (ipAddress != null)
                reg.setIpAddress(ipAddress);
            if (latitude != null)
                reg.setLatitude(latitude);
            if (longitude != null)
                reg.setLongitude(longitude);
            if (city != null)
                reg.setCity(city);
            if (country != null)
                reg.setCountry(country);

            incompleteRegistrationRepository.save(reg);
            logger.debug("Tracked incomplete registration step: {} for mobile: {}", step, mobileNumber);
        } catch (Exception e) {
            logger.error("Failed to track incomplete registration: {}", e.getMessage());
        }
    }

    /**
     * Aggregates storage usage from image and video services.
     */
    public live.chronogram.auth.dto.StorageUsageResponse getStorageUsage(Long userId) {
        String finalImageServiceUrl = imageServiceUrl + userId;
        String finalVideoServiceUrl = videoServiceUrl + userId;

        live.chronogram.auth.dto.ServiceStorageInfo imageInfo = new live.chronogram.auth.dto.ServiceStorageInfo(0, 0);
        live.chronogram.auth.dto.ServiceStorageInfo videoInfo = new live.chronogram.auth.dto.ServiceStorageInfo(0, 0);

        try {
            imageInfo = restTemplate.getForObject(finalImageServiceUrl,
                    live.chronogram.auth.dto.ServiceStorageInfo.class);
        } catch (Exception e) {
            logger.warn("Failed to fetch image storage usage for user {}: {}", userId, e.getMessage());
        }

        try {
            videoInfo = restTemplate.getForObject(finalVideoServiceUrl,
                    live.chronogram.auth.dto.ServiceStorageInfo.class);
        } catch (Exception e) {
            logger.warn("Failed to fetch video storage usage for user {}: {}", userId, e.getMessage());
        }

        // Aggregate and convert to GB
        long totalBytes = (imageInfo != null ? imageInfo.getTotalBytes() : 0) +
                (videoInfo != null ? videoInfo.getTotalBytes() : 0);
        Double usedGb = totalBytes / (1024.0 * 1024.0 * 1024.0);

        // Match StorageService logic: 10GB limit, 9GB warning
        Double limit = 10.0;

        // Simple rounding (inline for minimal dependencies)
        usedGb = Math.round(usedGb * 100.0) / 100.0;
        boolean warning = usedGb >= 9.0;

        return new live.chronogram.auth.dto.StorageUsageResponse(usedGb, limit, "GB", warning);
    }

    /**
     * Internal helper to update registration progress tracking.
     */
    @Transactional
    public void updateRegistrationProgress(Long userId, String step) {
        RegistrationProgress progress = registrationProgressRepository.findById(userId)
                .orElse(new RegistrationProgress(userId));

        switch (step) {
            case "OTP_SENT" -> progress.setOtpSent(true);
            case "OTP_VERIFIED" -> progress.setOtpVerified(true);
            case "PROFILE_CREATED" -> progress.setProfileCreated(true);
            case "DEVICE_REGISTERED" -> progress.setDeviceRegistered(true);
            case "SYNC_ENABLED" -> progress.setSyncEnabled(true);
            case "ACTIVE" -> {
                progress.setCompleted(true);
                User user = userRepository.findById(userId).orElse(null);
                if (user != null) {
                    user.setRegistrationStatus("ACTIVE");
                    userRepository.save(user);
                }
            }
        }
        registrationProgressRepository.save(progress);
    }

    /**
     * Nightly cleanup: Removes incomplete registrations older than 48 hours.
     */
    @Scheduled(cron = "0 0 2 * * *") // Runs every night at 2 AM
    @Transactional
    public void cleanupAbandonedRegistrations() {
        LocalDateTime cutoff = LocalDateTime.now(java.time.ZoneOffset.UTC).minusDays(2);

        // Find users who haven't completed registration and are older than 2 days
        List<User> abandonedUsers = userRepository.findAll().stream()
                .filter(u -> !"ACTIVE".equals(u.getRegistrationStatus()) &&
                        u.getCreatedTimestamp().isBefore(cutoff))
                .toList();

        if (!abandonedUsers.isEmpty()) {
            logger.info("Cleaning up {} abandoned registrations", abandonedUsers.size());
            for (User user : abandonedUsers) {
                // Remove sync status, storage usage, etc.
                syncStatusRepository.deleteById(user.getUserId());
                storageUsageRepository.deleteById(user.getUserId());
                registrationProgressRepository.deleteById(user.getUserId());
                userRepository.delete(user);
            }
        }
    }
}
