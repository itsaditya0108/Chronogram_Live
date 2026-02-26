package live.chronogram.auth.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import live.chronogram.auth.enums.OtpType;
import live.chronogram.auth.model.User;
import live.chronogram.auth.model.UserDevice;
import live.chronogram.auth.model.UserSession;
import live.chronogram.auth.model.UserStatus;
import live.chronogram.auth.repository.UserDeviceRepository;
import live.chronogram.auth.repository.UserRepository;
import live.chronogram.auth.repository.UserSessionRepository;
import live.chronogram.auth.repository.UserStatusRepository;
import live.chronogram.auth.security.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(AuthService.class);

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

    @Value("${app.jwt.refresh-token-validity-ms}")
    private long refreshTokenValidityInMs;

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
    @Transactional
    public String sendOtp(String mobileNumber, boolean isLoginAttempt) {
        String sanitizedMobile = sanitizePhoneNumber(mobileNumber);

        Optional<User> userOpt = userRepository.findByMobileNumber(sanitizedMobile);
        if (isLoginAttempt) {
            if (userOpt.isEmpty()) {
                throw new live.chronogram.auth.exception.AuthException(org.springframework.http.HttpStatus.NOT_FOUND,
                        "User not found. Please register.");
            }
            if (!"AC".equals(userOpt.get().getUserStatus().getUserStatusId())) {
                throw new live.chronogram.auth.exception.AuthException(org.springframework.http.HttpStatus.FORBIDDEN,
                        "Account is " + userOpt.get().getUserStatus().getName() + ". Cannot send OTP.");
            }
        } else {
            if (userOpt.isPresent()) {
                throw new live.chronogram.auth.exception.AuthException(org.springframework.http.HttpStatus.CONFLICT,
                        "User already registered. Please login.");
            }
        }

        return otpService.generateOtp(sanitizedMobile, OtpType.MOBILE_LOGIN);
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
    @Transactional
    public String sendEmailOtp(String mobileNumber) {
        // Check if there is a pending registration
        // NOTE: This method is primarily for Recovery or linking where User exists.
        // For new registration, we use the overloaded method or handle inside logic if
        // needed.
        // But the controller typically calls this with just mobileNumber for recovery?
        // Actually, for stateless registration, the frontend might not provide
        // mobileNumber directly
        // if they only have a token, OR they provide mobileNumber + RegistrationToken.

        // if they only have a token, OR they provide mobileNumber + RegistrationToken.

        String sanitizedMobile = sanitizePhoneNumber(mobileNumber);
        User user = userRepository.findByMobileNumber(sanitizedMobile)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.getEmail() == null || user.getEmail().isEmpty()) {
            throw new RuntimeException("No email registered for this user.");
        }

        return otpService.generateOtp(user.getEmail(), OtpType.EMAIL_VERIFICATION);
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
    @Transactional
    public String sendEmailOtp(String email, String registrationToken) {
        if (registrationToken == null || registrationToken.isEmpty()) {
            throw new RuntimeException("Registration token required for new users.");
        }

        io.jsonwebtoken.Claims claims = jwtTokenProvider.getClaimsFromRegistrationToken(registrationToken);
        String currentStep = (String) claims.get("step");

        if (!"EMAIL_REQUIRED".equals(currentStep)) {
            throw new RuntimeException("Invalid registration step. Cannot send email OTP.");
        }

        String formattedEmail = validateAndFormatEmail(email);

        // Ensure email is not already taken
        if (userRepository.findByEmail(formattedEmail).isPresent()) {
            throw new RuntimeException("Email already in use.");
        }

        // Generate OTP for the provided email (stateless, associated with email
        // address)
        return otpService.generateOtp(formattedEmail, OtpType.EMAIL_VERIFICATION);
    }

    @Transactional
    public String resendNewDeviceOtp(String temporaryToken) {
        if (temporaryToken == null || temporaryToken.isEmpty()) {
            throw new RuntimeException("Temporary token is required for resend.");
        }

        io.jsonwebtoken.Claims claims = jwtTokenProvider.getClaimsFromRegistrationToken(temporaryToken);
        String step = (String) claims.get("step");
        if (!"DEVICE_APPROVAL_REQUIRED".equals(step)) {
            throw new RuntimeException("Invalid temporary token for new device verification.");
        }

        String email = (String) claims.get("email");

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return otpService.generateOtp(user.getEmail(), OtpType.NEW_DEVICE_VERIFICATION);
    }

    /**
     * Resends an Email OTP directly by a registered email address.
     * 
     * @param email The target email.
     * @return The generated OTP string.
     */
    @Transactional
    public String resendEmailOtpByEmail(String email) {
        String formattedEmail = validateAndFormatEmail(email);
        User user = userRepository.findByEmail(formattedEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return otpService.generateOtp(user.getEmail(), OtpType.EMAIL_VERIFICATION);
    }

    /**
     * Entry point to verify OTP specifically for registration (NEW users).
     * Prevents operations if the user is already found in the system.
     * Delegates to the core @Transactional verifyOtpAndLogin.
     */
    public String verifyOtpForRegistration(String mobileNumber, String otpCode, String emailOtpCode,
            String deviceId, String simSerial, String pushToken,
            String deviceName, String deviceModel, String osName, String osVersion, String appVersion,
            Double latitude, Double longitude, String country, String city, String ipAddress, String userAgent) {

        String sanitizedMobile = sanitizePhoneNumber(mobileNumber);
        logger.info("Verifying registration OTP for mobile: {}", sanitizedMobile);

        if (userRepository.findByMobileNumber(sanitizedMobile).isPresent()) {
            throw new live.chronogram.auth.exception.AuthException(org.springframework.http.HttpStatus.CONFLICT,
                    "User already exists. Please login.");
        }

        // Call the core logic (which is @Transactional)
        // This will CREATE the user and COMMIT the transaction when it returns.
        // For NEW USER, verifyOtpAndLogin returns a RegistrationToken.
        return verifyOtpAndLogin(sanitizedMobile, otpCode, emailOtpCode, false, deviceId, simSerial, pushToken,
                deviceName, deviceModel, osName, osVersion, appVersion, latitude, longitude, country, city, ipAddress,
                userAgent);
    }

    /**
     * Entry point to verify OTP specifically for login (EXISTING users).
     * Stops operation if the user is not found.
     * Delegates to the core @Transactional verifyOtpAndLogin.
     */
    @Transactional(noRollbackFor = live.chronogram.auth.exception.AuthException.class)
    public String verifyOtpForLogin(String mobileNumber, String otpCode, String emailOtpCode, boolean isRecoveryFlow,
            String deviceId, String simSerial, String pushToken,
            String deviceName, String deviceModel, String osName, String osVersion, String appVersion,
            Double latitude, Double longitude, String country, String city, String ipAddress, String userAgent) {

        String sanitizedMobile = sanitizePhoneNumber(mobileNumber);
        logger.info("Verifying login OTP for mobile: {}", sanitizedMobile);

        if (userRepository.findByMobileNumber(sanitizedMobile).isEmpty()) {
            throw new live.chronogram.auth.exception.AuthException(org.springframework.http.HttpStatus.NOT_FOUND,
                    "User not found. Please register.");
        }

        return verifyOtpAndLogin(mobileNumber, otpCode, emailOtpCode, isRecoveryFlow, deviceId, simSerial, pushToken,
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
    @Transactional(noRollbackFor = live.chronogram.auth.exception.AuthException.class)
    public String verifyOtpAndLogin(String mobileNumber, String otpCode, String emailOtpCode, boolean isRecoveryFlow,
            String deviceId, String simSerial, String pushToken,
            String deviceName, String deviceModel, String osName, String osVersion, String appVersion,
            Double latitude, Double longitude, String country, String city, String ipAddress, String userAgent) {

        if (deviceId == null || deviceId.trim().isEmpty()) {
            throw new live.chronogram.auth.exception.AuthException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "DEVICE_ID_REQUIRED: deviceId is required.");
        }

        String sanitizedMobile = sanitizePhoneNumber(mobileNumber);
        logger.debug("Core verifyOtpAndLogin for mobile: {}", sanitizedMobile);

        // 1. Verify Mobile OTP (Always required)
        boolean isMobileVerified = false;
        try {
            isMobileVerified = otpService.verifyOtp(sanitizedMobile, OtpType.MOBILE_LOGIN, otpCode);
        } catch (live.chronogram.auth.exception.AuthException e) {
            // Only catch if it's the TOO_MANY_REQUESTS exception thrown by OtpService
            if (e.getStatus() == org.springframework.http.HttpStatus.TOO_MANY_REQUESTS) {
                // If Max Attempts Exceeded, block the user account temporarily if they are
                // registered
                Optional<User> userOpt = userRepository.findByMobileNumber(sanitizedMobile);
                if (userOpt.isPresent()) {
                    User userToBlock = userOpt.get();
                    Optional<live.chronogram.auth.model.UserStatus> blockedStatus = userStatusRepository.findById("BL");
                    blockedStatus.ifPresent(status -> {
                        userToBlock.setUserStatus(status);
                        userRepository.save(userToBlock);
                    });
                }
            }
            throw e; // Re-throw the AuthException (whether it was too many requests, etc.)
        }

        if (!isMobileVerified) {
            throw new live.chronogram.auth.exception.AuthException(org.springframework.http.HttpStatus.UNAUTHORIZED,
                    "Invalid Mobile OTP");
        }

        // 2. Find or Create User
        Optional<User> userOpt = userRepository.findByMobileNumber(sanitizedMobile);

        if (userOpt.isEmpty()) {
            // NEW USER FLOW - STATELESS
            // Return NOT a JWT, but a Registration Token
            // Step 1: Mobile Verified, Next: Email Required

            // STRICT SIM CHECK (Optional here, or defer to completeProfile?
            // Better to check here to reject invalid SIMs early)
            if (simSerial == null || simSerial.trim().isEmpty()) {
                throw new RuntimeException("SIM_REQUIRED: Registration requires a valid SIM card.");
            }

            return jwtTokenProvider.createRegistrationToken(sanitizedMobile, null, "EMAIL_REQUIRED");
        }

        User user = userOpt.get();

        // 3. Check Account Status
        if (!"AC".equals(user.getUserStatus().getUserStatusId())) {
            throw new RuntimeException("Account is " + user.getUserStatus().getName());
        }

        // 4. Device Logic
        boolean isDeviceTrusted = deviceService.isDeviceTrusted(user, deviceId);
        boolean hasOtherTrustedDevices = deviceService.hasAnyTrustedDevice(user.getUserId());
        boolean shouldTrustThisDevice = false;

        // Fetch all user devices ONCE to avoid redundant DB calls and ensure
        // availability
        List<UserDevice> allUserDevices = userDeviceRepository.findByUser_UserId(user.getUserId());

        if (isDeviceTrusted) {
            // Already trusted, proceed to login
            shouldTrustThisDevice = true;
        } else {
            // New Device Logic
            if (!hasOtherTrustedDevices) {
                // First device registration - Strict SIM Check handled above for new users.
                // For existing users logging in on a "first" device (e.g. after clearing all
                // devices),
                // we might implicitly trust if SIM is present?
                // For now, allow if no other trusted devices exist.
                shouldTrustThisDevice = true;
            } else {
                // Secondary Device Login
                if (isRecoveryFlow) {
                    // "Lost Device" Flow
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
                    // Normal "New Device" Flow

                    // CHECK: If Email OTP is provided, try to verify it immediately
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
                        // Trigger Notification
                        // We need to find trusted devices and send them an alert
                        List<UserDevice> trustedDevices = allUserDevices.stream()
                                .filter(d -> Boolean.TRUE.equals(d.getIsTrusted()))
                                .toList();

                        for (UserDevice td : trustedDevices) {
                            String safeDeviceName = (deviceName != null && !deviceName.isEmpty()) ? deviceName
                                    : "Unknown Device";
                            String safeDeviceModel = (deviceModel != null && !deviceModel.isEmpty()) ? deviceModel
                                    : "Unknown Model";

                            notificationService.sendNotification(td.getPushToken(),
                                    "New Login Attempt",
                                    "Attempt details: " + safeDeviceName + " (" + safeDeviceModel + ")",
                                    "LOGIN_APPROVAL_REQUEST",
                                    "{\"targetDeviceId\": \"" + deviceId + "\"}");
                        }

                        // Trigger New Device Verification OTP to EMAIL
                        if (user.getEmail() != null && !user.getEmail().isEmpty()) {
                            otpService.generateOtp(user.getEmail(),
                                    live.chronogram.auth.enums.OtpType.NEW_DEVICE_VERIFICATION);

                            String email = user.getEmail();
                            String maskedEmail = email.replaceAll("(^[^@]{3}|(?!^)\\G)[^@]", "$1*");
                            // Simple mask: first 3 chars visible, rest hidden before @.
                            // Better simple regex for "a***@gmail.com":
                            int atIndex = email.indexOf("@");
                            if (atIndex > 1) {
                                String namePart = email.substring(0, atIndex);
                                String domainPart = email.substring(atIndex);
                                if (namePart.length() > 2) {
                                    maskedEmail = namePart.substring(0, 2) + "***" + domainPart;
                                } else {
                                    maskedEmail = namePart + "***" + domainPart;
                                }
                            } else {
                                maskedEmail = email; // Fallback
                            }

                            String temporaryToken = jwtTokenProvider.createRegistrationToken(sanitizedMobile,
                                    user.getEmail(), "DEVICE_APPROVAL_REQUIRED");

                            throw new live.chronogram.auth.exception.DeviceApprovalRequiredException(
                                    "APPROVAL_REQUIRED: OTP sent to registered email.", maskedEmail, temporaryToken);
                        } else {
                            throw new live.chronogram.auth.exception.EmailLinkingRequiredException(
                                    "EMAIL_REQUIRED: No email registered. Cannot verify new device.");
                        }
                    }
                }
            }
        }

        // Send Login Alert to OTHER trusted devices (if login successful)
        if (shouldTrustThisDevice) {
            List<UserDevice> otherTrustedDevices = allUserDevices.stream()
                    .filter(d -> Boolean.TRUE.equals(d.getIsTrusted()) && !d.getDeviceId().equals(deviceId))
                    .toList();

            for (UserDevice td : otherTrustedDevices) {
                notificationService.sendLoginAlert(td.getPushToken(), deviceName, (latitude + "," + longitude));
            }
        }

        // Register/Update Device
        UserDevice device = deviceService.registerOrUpdateDevice(user, deviceId, simSerial, pushToken, deviceName,
                deviceModel,
                osName, osVersion, appVersion, latitude, longitude, country, city, shouldTrustThisDevice);

        // Generate Tokens
        String accessToken = jwtTokenProvider.createAccessToken(user.getUserId(), "USER", device.getUserDeviceId());
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getUserId());

        // Create Session
        UserSession session = new UserSession();
        session.setUser(user);
        session.setUserDevice(device);
        session.setRefreshTokenHash(refreshToken);
        session.setExpiresTimestamp(LocalDateTime.now().plusNanos(refreshTokenValidityInMs * 1000000));
        session.setIsRevoked(false);
        session.setExpiresTimestamp(LocalDateTime.now().plusNanos(refreshTokenValidityInMs * 1000000));
        session.setIsRevoked(false);
        session.setIpAddress(ipAddress);
        session.setLatitude(latitude);
        session.setLongitude(longitude);
        session.setCountry(country);
        session.setCity(city);
        userSessionRepository.save(session);

        // Save Login History
        saveLoginHistory(user, device, ipAddress, userAgent, true, null, latitude, longitude, city, country);

        return accessToken;
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
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new RuntimeException("Invalid refresh token");
        }

        Long userId = jwtTokenProvider.getUserIdFromToken(refreshToken);

        // Use hash in real world
        Optional<UserSession> sessionOpt = userSessionRepository.findByRefreshTokenHash(refreshToken);

        if (sessionOpt.isEmpty()) {
            throw new RuntimeException("Session not found or revoked");
        }

        UserSession session = sessionOpt.get();
        if (session.getIsRevoked()) {
            throw new RuntimeException("Session is revoked");
        }

        if (session.getExpiresTimestamp().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Session expired");
        }

        // Rotate Refresh Token? (Optional security practice)
        // For now, just issue new Access Token

        return jwtTokenProvider.createAccessToken(userId, "USER", session.getUserDevice().getUserDeviceId());
    }

    @Transactional
    public void logout(String refreshToken) {
        Optional<UserSession> sessionOpt = userSessionRepository.findByRefreshTokenHash(refreshToken);
        sessionOpt.ifPresent(session -> {
            session.setIsRevoked(true);
            userSessionRepository.save(session);
        });
    }

    /**
     * Handles the flow when a user validates an Email OTP strictly prompted by a
     * New Device.
     * Marks the device as trusted upon success and generates tokens.
     * 
     * @return The final Access Token granting access from the new device.
     */
    @Transactional
    public String verifyNewDevice(live.chronogram.auth.dto.VerifyNewDeviceRequest request, String ipAddress,
            String userAgent) {
        String sanitizedMobile = sanitizePhoneNumber(request.getMobileNumber());
        logger.info("Verifying new device for mobile: {}", sanitizedMobile);
        // 1. Find User (Look up by mobile first)
        User user = userRepository.findByMobileNumber(sanitizedMobile)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.getEmail() == null || user.getEmail().isEmpty()) {
            throw new RuntimeException("No email registered for this user.");
        }

        // 2. Verify OTP against EMAIL
        boolean isVerified = otpService.verifyOtp(user.getEmail(),
                live.chronogram.auth.enums.OtpType.NEW_DEVICE_VERIFICATION, request.getOtp());
        if (!isVerified) {
            throw new RuntimeException("Invalid OTP for new device verification");
        }

        // 3. Register/Trust Device
        // Since the initial login attempt threw an exception (rolling back DB),
        // we must register the device here as "Trusted".
        UserDevice device = deviceService.registerOrUpdateDevice(user,
                request.getDeviceId(),
                null, // simSerial might not be passed in this step? Or request should have it?
                null, // pushToken might be missing
                request.getDeviceName(),
                request.getDeviceModel(),
                request.getOsName(),
                request.getOsVersion(),
                request.getAppVersion(),
                request.getLatitude(),
                request.getLongitude(),
                request.getCountry(),
                request.getCity(),
                true); // IMP: Trust this device

        // 4. Create Session & Token (Same logic as login)
        String accessToken = jwtTokenProvider.createAccessToken(user.getUserId(), "USER", device.getUserDeviceId());
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getUserId());

        UserSession session = new UserSession();
        session.setUser(user);
        session.setUserDevice(device);
        session.setRefreshTokenHash(refreshToken);
        session.setExpiresTimestamp(LocalDateTime.now().plusNanos(refreshTokenValidityInMs * 1000000));
        session.setIsRevoked(false);
        session.setIpAddress(ipAddress);
        session.setLatitude(request.getLatitude());
        session.setLongitude(request.getLongitude());
        session.setCountry(request.getCountry());
        session.setCity(request.getCity());
        userSessionRepository.save(session);

        // Notify other devices of new login? (Optional but good)
        // Send Login Alert to OTHER trusted devices
        List<UserDevice> allUserDevices = userDeviceRepository.findByUser_UserId(user.getUserId());
        List<UserDevice> otherTrustedDevices = allUserDevices.stream()
                .filter(d -> Boolean.TRUE.equals(d.getIsTrusted()) && !d.getDeviceId().equals(request.getDeviceId()))
                .toList();

        for (UserDevice td : otherTrustedDevices) {
            String loc = (request.getLatitude() != null && request.getLongitude() != null)
                    ? (request.getLatitude() + "," + request.getLongitude())
                    : "Unknown Location";
            notificationService.sendLoginAlert(td.getPushToken(), request.getDeviceName(), loc);
        }

        // Save Login History
        saveLoginHistory(user, device, ipAddress, userAgent, true, null, request.getLatitude(), request.getLongitude(),
                request.getCity(), request.getCountry());

        return accessToken;
    }

    /**
     * Initiates linking a new email to an account, generating an Email OTP.
     * Extracts mobile info prioritizing the provided token if present.
     */
    @Transactional
    public String linkEmail(live.chronogram.auth.dto.LinkEmailRequest request) {
        String mobileNumber = sanitizePhoneNumber(request.getMobileNumber());

        // Prioritize Token if present
        if (request.getRegistrationToken() != null && !request.getRegistrationToken().isEmpty()) {
            io.jsonwebtoken.Claims claims = jwtTokenProvider
                    .getClaimsFromRegistrationToken(request.getRegistrationToken());
            mobileNumber = claims.getSubject();
        }

        // Validate Mobile (Ensure not already registered) e.g. race condition check?
        if (userRepository.findByMobileNumber(mobileNumber).isPresent()) {
            throw new RuntimeException("User already registered.");
        }

        String formattedEmail = validateAndFormatEmail(request.getEmail());

        if (userRepository.findByEmail(formattedEmail).isPresent()) {
            throw new RuntimeException("Email already in use.");
        }

        return otpService.generateOtp(formattedEmail, live.chronogram.auth.enums.OtpType.EMAIL_LINKING);
    }

    /**
     * Verifies the OTP sent during the linking process.
     * Completes this step by generating a token allowing the user to proceed to
     * Profile Completion.
     */
    @Transactional
    public String verifyLinkEmail(live.chronogram.auth.dto.VerifyEmailRequest request) {
        String mobileNumber = request.getMobileNumber();

        if (request.getRegistrationToken() != null && !request.getRegistrationToken().isEmpty()) {
            io.jsonwebtoken.Claims claims = jwtTokenProvider
                    .getClaimsFromRegistrationToken(request.getRegistrationToken());
            mobileNumber = claims.getSubject();
        }

        String formattedEmail = validateAndFormatEmail(request.getEmail());
        boolean isVerified = otpService.verifyOtp(formattedEmail, live.chronogram.auth.enums.OtpType.EMAIL_LINKING,
                request.getOtp());
        if (!isVerified) {
            throw new RuntimeException("Invalid Email OTP");
        }

        // Return Next Step Token
        return jwtTokenProvider.createRegistrationToken(mobileNumber, formattedEmail, "PROFILE_REQUIRED");
    }

    /**
     * The final step of Registration. Takes user demographic data, validates prior
     * stateless steps
     * by extracting the mobile and email from the valid RegistrationToken, saves
     * the comprehensive
     * User entity, and generates their first session tokens.
     */
    @Transactional
    public String completeProfile(live.chronogram.auth.dto.CompleteProfileRequest request, String ipAddress,
            String userAgent) {
        String mobileNumber = null;
        String email = null;

        if (request.getRegistrationToken() != null && !request.getRegistrationToken().isEmpty()) {
            io.jsonwebtoken.Claims claims = jwtTokenProvider
                    .getClaimsFromRegistrationToken(request.getRegistrationToken());
            mobileNumber = claims.getSubject();
            email = (String) claims.get("email");

            if (!"PROFILE_REQUIRED".equals(claims.get("step"))) {
                throw new RuntimeException("Invalid registration step. Please verify email first.");
            }
            logger.info("Completing profile for mobile (from token): {}", mobileNumber);
        } else {
            mobileNumber = sanitizePhoneNumber(request.getMobileNumber());
            logger.info("Completing profile for mobile: {}", mobileNumber);
        }

        if (userRepository.findByMobileNumber(mobileNumber).isPresent()) {
            throw new RuntimeException("User already exists.");
        }

        // Validation of complete profile parameters
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            throw new RuntimeException("Name is required to complete profile.");
        }
        if (request.getDob() == null || request.getDob().trim().isEmpty()) {
            throw new RuntimeException("Date of Birth is required to complete profile.");
        }
        if (email == null || email.trim().isEmpty()) {
            throw new RuntimeException("Verified email is missing from registration flow.");
        }

        // CREATE USER (Finally!)
        User newUser = new User();
        newUser.setMobileNumber(mobileNumber);
        newUser.setEmail(email);
        newUser.setName(request.getName().trim());
        try {
            java.time.LocalDate dateOfBirth = java.time.LocalDate.parse(request.getDob());

            // Age Validation (Must be 12+)
            int age = java.time.Period.between(dateOfBirth, java.time.LocalDate.now()).getYears();
            if (age < 12) {
                throw new RuntimeException("You must be at least 12 years old to register.");
            }

            newUser.setDob(dateOfBirth);
        } catch (java.time.format.DateTimeParseException e) {
            throw new RuntimeException("Invalid Date of Birth format. Please use YYYY-MM-DD.");
        }
        newUser.setMobileVerified(true);
        newUser.setEmailVerified(true);
        newUser.setProfilePictureUrl(
                "https://api.dicebear.com/9.x/micah/svg?seed=" + request.getName().replace(" ", "+"));

        Optional<UserStatus> activeStatus = userStatusRepository.findById("AC");
        activeStatus.ifPresent(newUser::setUserStatus);

        User savedUser = userRepository.save(newUser);

        // Register/Update Device (FIX: Now capturing device info during registration)
        // Note: For new registration, we treat it as trusted?
        // Usually yes, if they just verified MOBILE + EMAIL on this device.
        UserDevice device = deviceService.registerOrUpdateDevice(savedUser,
                request.getDeviceId(),
                request.getSimSerial(),
                request.getPushToken(),
                request.getDeviceName(),
                request.getDeviceModel(),
                request.getOsName(),
                request.getOsVersion(),
                request.getAppVersion(),
                request.getLatitude(),
                request.getLongitude(),
                request.getCountry(),
                request.getCity(),
                true); // Trusted on first registration

        // Generate Tokens
        String accessToken = jwtTokenProvider.createAccessToken(savedUser.getUserId(), "USER",
                device.getUserDeviceId());
        String refreshToken = jwtTokenProvider.createRefreshToken(savedUser.getUserId());

        // Create Session
        UserSession session = new UserSession();
        session.setUser(savedUser);
        session.setUserDevice(device); // Link Session to Device
        session.setRefreshTokenHash(refreshToken);
        session.setExpiresTimestamp(LocalDateTime.now().plusNanos(refreshTokenValidityInMs * 1000000));
        session.setIsRevoked(false);
        session.setIpAddress(ipAddress);
        session.setLatitude(request.getLatitude());
        session.setLongitude(request.getLongitude());
        session.setCountry(request.getCountry());
        session.setCity(request.getCity());
        userSessionRepository.save(session);

        // Save Login History (First Login)
        saveLoginHistory(savedUser, device, ipAddress, userAgent, true, null, request.getLatitude(),
                request.getLongitude(), request.getCity(), request.getCountry());

        return accessToken;
    }

    /**
     * Middle-step in stateless registration: Verifies the email via OTP and
     * progresses the registration token.
     */
    @Transactional
    public String verifyEmailOtpForRegistration(String email, String otpCode, String registrationToken) {
        if (registrationToken == null || registrationToken.isEmpty()) {
            throw new RuntimeException("Registration token required.");
        }

        io.jsonwebtoken.Claims claims = jwtTokenProvider.getClaimsFromRegistrationToken(registrationToken);
        String mobileNumber = claims.getSubject();
        String currentStep = (String) claims.get("step");

        if (!"EMAIL_REQUIRED".equals(currentStep)) {
            throw new RuntimeException("Invalid registration step.");
        }

        String formattedEmail = validateAndFormatEmail(email);
        boolean isVerified = otpService.verifyOtp(formattedEmail, OtpType.EMAIL_VERIFICATION, otpCode);
        if (!isVerified) {
            throw new RuntimeException("Invalid Email OTP");
        }

        // Generate Next Token (Step: PROFILE_REQUIRED)
        // Store validated email in token so we trust it in next step
        return jwtTokenProvider.createRegistrationToken(mobileNumber, formattedEmail, "PROFILE_REQUIRED");
    }

    public live.chronogram.auth.dto.UserResponse getUserDetails(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return new live.chronogram.auth.dto.UserResponse(
                user.getUserId(),
                user.getName(),
                user.getEmail(),
                user.getMobileNumber(),
                user.getDob() != null ? user.getDob().toString() : null,
                user.getProfilePictureUrl(),
                user.getMobileVerified(),
                user.getEmailVerified(),
                user.getUserStatus() != null ? user.getUserStatus().getName() : "Unknown");
    }

    public boolean isUserRegistered(String mobileNumber) {
        String sanitizedMobile = sanitizePhoneNumber(mobileNumber);
        return userRepository.findByMobileNumber(sanitizedMobile).isPresent();
    }

    private void saveLoginHistory(User user, UserDevice device, String ipAddress, String userAgent, boolean success,
            String failureReason, Double latitude, Double longitude, String city, String country) {
        try {
            live.chronogram.auth.model.LoginHistory history = new live.chronogram.auth.model.LoginHistory();
            history.setUserId(user.getUserId());
            history.setIpAddress(ipAddress);
            history.setUserAgent(userAgent);
            history.setSuccess(success);
            history.setFailureReason(failureReason);
            history.setCreatedTimestamp(LocalDateTime.now());

            if (device != null) {
                history.setDeviceModel(device.getDeviceModel());
                // history.setDeviceType(device.getDeviceType()); // If available
                history.setOs(device.getOsName() + " " + device.getOsVersion());
                history.setBrowser(null); // App doesn't usually send browser info unless parsed from UA
            }

            history.setLatitude(latitude);
            history.setLongitude(longitude);
            history.setCity(city);
            history.setCountry(country);

            loginHistoryRepository.save(history);
        } catch (Exception e) {
            logger.error("Failed to save login history: {}", e.getMessage());
            // Don't block login if history fails
        }
    }

    private String sanitizePhoneNumber(String mobileNumber) {
        if (mobileNumber == null) {
            throw new RuntimeException("Mobile number is required");
        }
        // Remove spaces, dashes, parentheses
        String cleaned = mobileNumber.replaceAll("[\\s\\-\\(\\)]", "");

        // Optional '+' at the start followed by 10 to 15 digits
        if (!cleaned.matches("^\\+?\\d{10,15}$")) {
            throw new RuntimeException("Invalid mobile number format.");
        }

        // If it's a 10 digit number, assume India (+91)
        if (cleaned.matches("^\\d{10}$")) {
            return "+91" + cleaned;
        }

        // If it starts with 91 and is 12 digits, add +
        if (cleaned.matches("^91\\d{10}$")) {
            return "+" + cleaned;
        }

        // Return as is if it already has + or is non-standard,
        // validation can happen downstream if needed or here.
        return cleaned;
    }

    private String validateAndFormatEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            throw new RuntimeException("Email is required.");
        }

        String trimmedEmail = email.trim().toLowerCase();

        if (trimmedEmail.length() > 254) {
            throw new RuntimeException("Invalid email format (too long).");
        }

        // Stronger email regex: restricts special characters and ensures standard
        // domains. Also limits local-part (before @) to Max 64 characters.
        String emailRegex = "^[A-Za-z0-9._%+-]{1,64}@[A-Za-z0-9.-]+\\.[A-Za-z]{2,20}$";
        if (!trimmedEmail.matches(emailRegex)) {
            throw new RuntimeException("Invalid email format.");
        }

        return trimmedEmail;
    }
}
