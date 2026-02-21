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

    @Transactional
    public String sendOtp(String mobileNumber) {
        String sanitizedMobile = sanitizePhoneNumber(mobileNumber);
        return otpService.generateOtp(sanitizedMobile, OtpType.MOBILE_LOGIN);
    }

    // Recovery: Send Email OTP
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

        // Ensure email is not already taken
        if (userRepository.findByEmail(email).isPresent()) {
            throw new RuntimeException("Email already in use.");
        }

        // Generate OTP for the provided email (stateless, associated with email
        // address)
        return otpService.generateOtp(email, OtpType.EMAIL_VERIFICATION);
    }

    public String verifyOtpForRegistration(String mobileNumber, String otpCode, String emailOtpCode,
            String deviceId, String simSerial, String pushToken,
            String deviceName, String deviceModel, String osName, String osVersion, String appVersion,
            Double latitude, Double longitude, String country, String city, String ipAddress, String userAgent) {

        String sanitizedMobile = sanitizePhoneNumber(mobileNumber);
        logger.info("Verifying registration OTP for mobile: {}", sanitizedMobile);

        if (userRepository.findByMobileNumber(sanitizedMobile).isPresent()) {
            throw new RuntimeException("User already exists. Please login.");
        }

        // Call the core logic (which is @Transactional)
        // This will CREATE the user and COMMIT the transaction when it returns.
        // For NEW USER, verifyOtpAndLogin returns a RegistrationToken.
        return verifyOtpAndLogin(sanitizedMobile, otpCode, emailOtpCode, false, deviceId, simSerial, pushToken,
                deviceName, deviceModel, osName, osVersion, appVersion, latitude, longitude, country, city, ipAddress,
                userAgent);
    }

    @Transactional
    public String verifyOtpForLogin(String mobileNumber, String otpCode, String emailOtpCode, boolean isRecoveryFlow,
            String deviceId, String simSerial, String pushToken,
            String deviceName, String deviceModel, String osName, String osVersion, String appVersion,
            Double latitude, Double longitude, String country, String city, String ipAddress, String userAgent) {

        String sanitizedMobile = sanitizePhoneNumber(mobileNumber);
        logger.info("Verifying login OTP for mobile: {}", sanitizedMobile);

        if (userRepository.findByMobileNumber(sanitizedMobile).isEmpty()) {
            throw new RuntimeException("User not found. Please register.");
        }

        return verifyOtpAndLogin(mobileNumber, otpCode, emailOtpCode, isRecoveryFlow, deviceId, simSerial, pushToken,
                deviceName, deviceModel, osName, osVersion, appVersion, latitude, longitude, country, city, ipAddress,
                userAgent);
    }

    @Transactional
    public String verifyOtpAndLogin(String mobileNumber, String otpCode, String emailOtpCode, boolean isRecoveryFlow,
            String deviceId, String simSerial, String pushToken,
            String deviceName, String deviceModel, String osName, String osVersion, String appVersion,
            Double latitude, Double longitude, String country, String city, String ipAddress, String userAgent) {

        String sanitizedMobile = sanitizePhoneNumber(mobileNumber);
        logger.debug("Core verifyOtpAndLogin for mobile: {}", sanitizedMobile);

        // 1. Verify Mobile OTP (Always required)
        boolean isMobileVerified = otpService.verifyOtp(sanitizedMobile, OtpType.MOBILE_LOGIN, otpCode);
        if (!isMobileVerified) {
            throw new RuntimeException("Invalid Mobile OTP");
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

                            throw new live.chronogram.auth.exception.DeviceApprovalRequiredException(
                                    "APPROVAL_REQUIRED: OTP sent to registered email.", maskedEmail);
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

    @Transactional
    public void linkEmail(live.chronogram.auth.dto.LinkEmailRequest request) {
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

        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new RuntimeException("Email already in use.");
        }

        otpService.generateOtp(request.getEmail(), live.chronogram.auth.enums.OtpType.EMAIL_LINKING);
    }

    @Transactional
    public String verifyLinkEmail(live.chronogram.auth.dto.VerifyEmailRequest request) {
        String mobileNumber = request.getMobileNumber();

        if (request.getRegistrationToken() != null && !request.getRegistrationToken().isEmpty()) {
            io.jsonwebtoken.Claims claims = jwtTokenProvider
                    .getClaimsFromRegistrationToken(request.getRegistrationToken());
            mobileNumber = claims.getSubject();
        }

        boolean isVerified = otpService.verifyOtp(request.getEmail(), live.chronogram.auth.enums.OtpType.EMAIL_LINKING,
                request.getOtp());
        if (!isVerified) {
            throw new RuntimeException("Invalid Email OTP");
        }

        // Return Next Step Token
        return jwtTokenProvider.createRegistrationToken(mobileNumber, request.getEmail(), "PROFILE_REQUIRED");
    }

    @Transactional
    public String completeProfile(live.chronogram.auth.dto.CompleteProfileRequest request, String ipAddress,
            String userAgent) {
        String sanitizedMobile = sanitizePhoneNumber(request.getMobileNumber());
        logger.info("Completing profile for mobile: {}", sanitizedMobile);
        String mobileNumber = sanitizedMobile;
        String email = null;

        if (request.getRegistrationToken() != null && !request.getRegistrationToken().isEmpty()) {
            io.jsonwebtoken.Claims claims = jwtTokenProvider
                    .getClaimsFromRegistrationToken(request.getRegistrationToken());
            mobileNumber = claims.getSubject();
            email = (String) claims.get("email");

            if (!"PROFILE_REQUIRED".equals(claims.get("step"))) {
                throw new RuntimeException("Invalid registration step. Please verify email first.");
            }
        }

        if (userRepository.findByMobileNumber(mobileNumber).isPresent()) {
            throw new RuntimeException("User already exists.");
        }

        // CREATE USER (Finally!)
        User newUser = new User();
        newUser.setMobileNumber(mobileNumber);
        newUser.setEmail(email);
        newUser.setName(request.getName());
        newUser.setDob(java.time.LocalDate.parse(request.getDob()));
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

        boolean isVerified = otpService.verifyOtp(email, OtpType.EMAIL_VERIFICATION, otpCode);
        if (!isVerified) {
            throw new RuntimeException("Invalid Email OTP");
        }

        // Generate Next Token (Step: PROFILE_REQUIRED)
        // Store validated email in token so we trust it in next step
        return jwtTokenProvider.createRegistrationToken(mobileNumber, email, "PROFILE_REQUIRED");
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

        // If it's a 10 digit number, assume India (+91)
        if (cleaned.matches("\\d{10}")) {
            return "+91" + cleaned;
        }

        // If it starts with 91 and is 12 digits, add +
        if (cleaned.matches("91\\d{10}")) {
            return "+" + cleaned;
        }

        // Return as is if it already has + or is non-standard,
        // validation can happen downstream if needed or here.
        return cleaned;
    }
}
