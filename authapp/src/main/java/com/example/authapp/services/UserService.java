package com.example.authapp.services;

import com.example.authapp.dto.*;
import com.example.authapp.entity.*;
import com.example.authapp.exception.ApiException;
import com.example.authapp.repository.*;
import com.example.authapp.util.IpUtil;
import com.example.authapp.util.PhoneUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final OtpVerificationRepository otpRepository;
    private final EmailService emailService;
    private final OtpService otpService;
    private final SmsService smsService;
    private final UserStatusRepository userStatusRepository;
    private final LoginHistoryService loginHistoryService;
    private final UserDeviceRepository userDeviceRepository;
    private final UserSessionRepository userSessionRepository;
    private final JwtService jwtService;
    private final IpLocationService ipLocationService;
    private final UserDeviceService userDeviceService;
    private final SecurityHelperService securityHelperService;

    private final AdminRepository adminRepository;

    public UserService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            OtpVerificationRepository otpRepository,
            EmailService emailService,
            OtpService otpService,
            SmsService smsService,
            UserStatusRepository userStatusRepository,
            LoginHistoryService loginHistoryService,
            UserDeviceRepository userDeviceRepository,
            UserSessionRepository userSessionRepository,
            JwtService jwtService,
            IpLocationService ipLocationService,
            UserDeviceService userDeviceService,
            SecurityHelperService securityHelperService,
            AdminRepository adminRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.otpRepository = otpRepository;
        this.emailService = emailService;
        this.otpService = otpService;
        this.smsService = smsService;
        this.userStatusRepository = userStatusRepository;
        this.loginHistoryService = loginHistoryService;
        this.userDeviceRepository = userDeviceRepository;
        this.userSessionRepository = userSessionRepository;
        this.jwtService = jwtService;
        this.ipLocationService = ipLocationService;
        this.userDeviceService = userDeviceService;
        this.securityHelperService = securityHelperService;
        this.adminRepository = adminRepository;
    }

    /* ================= REGISTER ================= */
    @Transactional
    public User register(RegisterRequest request) {
        log.info("AUTH | REGISTER | START | email={} | phone={}", request.getEmail(), request.getPhone());

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ApiException("EMAIL_EXISTS");
        }

        String normalizedPhone = PhoneUtil.normalizeIndianPhone(request.getPhone());

        if (userRepository.existsByPhone(normalizedPhone)) {
            throw new ApiException("PHONE_ALREADY_EXISTS");
        }

        UserStatus activeStatus = userStatusRepository.findById("01")
                .orElseThrow(() -> new ApiException("STATUS_NOT_FOUND"));

        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setPhone(normalizedPhone);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setEmailVerified(false);
        user.setPhoneVerified(false);
        user.setStatus(activeStatus);
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);

        User savedUser = userRepository.save(user);

        // Record device if provided
        if (request.getDeviceContext() != null) {
            UserDevice device = userDeviceService.saveOrUpdateUserDevice(savedUser, request.getDeviceContext());
            if (device != null) {
                device.setDeviceTrusted(true); // Trust the registration device
                userDeviceRepository.save(device);
            }
        }

        // Send email OTP
        String otp = otpService.generateOtp();
        OtpVerification entity = new OtpVerification();
        entity.setUser(savedUser);
        entity.setTarget(savedUser.getEmail());
        entity.setOtpCode(otp);
        entity.setOtpType(OtpVerification.OtpType.EMAIL_VERIFICATION);
        entity.setExpiresAt(LocalDateTime.now().plusMinutes(10));

        otpRepository.save(entity);
        emailService.sendOtpEmail(savedUser.getEmail(), otp);

        return savedUser;
    }

    /* ================= LOGIN ================= */
    @Transactional
    public LoginResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        DeviceContextDto deviceContext = request.getDeviceContext();

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ApiException("INVALID_CREDENTIALS"));

        if (!"Active".equalsIgnoreCase(user.getStatus().getName())) {
            if (user.getStatusReason() != null && !user.getStatusReason().isEmpty()) {
                throw new ApiException("USER_BLOCKED", "Blocked: " + user.getStatusReason());
            }
            throw new ApiException("USER_NOT_ACTIVE");
        }

        if (!user.isEmailVerified()) {
            throw new ApiException("EMAIL_NOT_VERIFIED");
        }

        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now())) {
            throw new ApiException("ACCOUNT_LOCKED");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {

            // Record failure (REQUIRES_NEW)
            securityHelperService.processFailedLogin(user);

            // Record history (REQUIRES_NEW)
            loginHistoryService.recordLoginAttempt(
                    user.getId(),
                    user.getName(),
                    false,
                    "INVALID_CREDENTIALS",
                    httpRequest,
                    deviceContext);

            throw new ApiException("INVALID_CREDENTIALS");
        }
        UserDevice device = userDeviceService.saveOrUpdateUserDevice(user, deviceContext);

        // DEVICE VERIFICATION LOGIC
        if (device != null) {
            // First device ever → auto-trust
            if (userHasNoTrustedDevices(user)) {
                device.setDeviceTrusted(true);
                userDeviceRepository.save(device);
            }
            // Existing but untrusted device → requires verification
            else if (!device.isDeviceTrusted()) {
                otpService.sendNewDeviceOtp(user, device);
                throw new ApiException("NEW_DEVICE_VERIFICATION_REQUIRED");
            }
        }

        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);

        SessionCreationResult sessionResult = null;
        if (device != null) {
            sessionResult = createUserSession(user, device, httpRequest, deviceContext);
            if (request.isLogoutOtherDevices()) {
                userSessionRepository.revokeAllOtherSessions(user.getId(), sessionResult.getSession().getSessionId());
            }
        }

        String accessToken = jwtService.generateAccessToken(user,
                sessionResult != null ? sessionResult.getSession().getSessionId() : null);

        loginHistoryService.recordLoginAttempt(user.getId(), user.getName(), true, null, httpRequest, deviceContext);

        // CHECK IF USER IS ADMIN AND GENERATE TOKEN
        // CHECK IF USER IS ADMIN AND GENERATE TOKEN
        boolean isAdmin = false;
        String adminToken = null;
        try {
            log.error("DEBUG_ADMIN_CHECK: Login - Checking admin status for email: '{}'", user.getEmail());
            java.util.Optional<Admin> adminOpt = adminRepository.findByEmail(user.getEmail());

            if (adminOpt.isPresent()) {
                log.error("DEBUG_ADMIN_CHECK: Admin found! Active status: {}", adminOpt.get().getIsActive());
                if (Boolean.TRUE.equals(adminOpt.get().getIsActive())) {
                    String potentialToken = jwtService.generateAdminToken(adminOpt.get().getAdminId(),
                            adminOpt.get().getRole());
                    if (potentialToken != null) {
                        adminToken = potentialToken;
                        isAdmin = true;
                        log.error("DEBUG_ADMIN_CHECK: Admin token generated! isAdmin=true");
                    } else {
                        log.error("DEBUG_ADMIN_CHECK: Admin token generation failed (null)");
                    }
                } else {
                    log.error("DEBUG_ADMIN_CHECK: Admin is INACTIVE");
                }
            } else {
                log.error("DEBUG_ADMIN_CHECK: No Admin found for email: '{}'", user.getEmail());
            }
        } catch (Exception e) {
            log.error("DEBUG_ADMIN_CHECK: Exception during admin check", e);
        }

        return new LoginResponse(
                accessToken,
                sessionResult != null ? sessionResult.getRawRefreshToken() : null,
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getPhone(),
                user.isEmailVerified(),
                user.isPhoneVerified(),
                user.getStatus().getName(),
                isAdmin,
                adminToken);
    }

    /* ================= SESSION ================= */
    private SessionCreationResult createUserSession(User user, UserDevice device, HttpServletRequest request,
            DeviceContextDto deviceContext) {
        String rawToken = UUID.randomUUID().toString();
        String ip = IpUtil.getClientIp(request);

        UserSession session = new UserSession();
        session.setUser(user);
        session.setUserDevice(device);
        session.setRefreshTokenHash(passwordEncoder.encode(rawToken));
        session.setIpAddress(ip);
        session.setExpiresTimestamp(LocalDateTime.now().plusDays(30));
        session.setRevoked(false);

        // Store coordinates if provided
        if (deviceContext != null) {
            session.setUserName(user.getName());
            session.setDeviceModel(deviceContext.getDeviceModel());

            if (deviceContext.getLocation() != null) {
                session.setLatitude(deviceContext.getLocation().getLatitude());
                session.setLongitude(deviceContext.getLocation().getLongitude());
                session.setAccuracy(deviceContext.getLocation().getAccuracy());
            }
        }

        // Fetch location details from IP
        try {
            IpLocationResponse location = ipLocationService.lookup(ip);
            if (location != null && "success".equalsIgnoreCase(location.getStatus())) {
                session.setCountry(location.getCountry());
                session.setCity(location.getCity());
            }
        } catch (Exception e) {
            // Ignore location fetch failures
        }

        userSessionRepository.save(session);
        return new SessionCreationResult(session, rawToken);
    }

    @Transactional
    public LoginResponse refreshAccessToken(RefreshTokenRequest request) {
        List<UserSession> sessions = userSessionRepository.findAllActiveSessions();

        UserSession matched = sessions.stream()
                .filter(s -> passwordEncoder.matches(request.getRefreshToken(), s.getRefreshTokenHash()))
                .findFirst()
                .orElseThrow(() -> new ApiException("INVALID_REFRESH_TOKEN"));

        if (matched.getExpiresTimestamp().isBefore(LocalDateTime.now())) {
            matched.setRevoked(true);
            userSessionRepository.save(matched);
            throw new ApiException("REFRESH_TOKEN_EXPIRED");
        }

        matched.setRevoked(true);
        userSessionRepository.save(matched);

        UserSession newSession = new UserSession();
        newSession.setUser(matched.getUser());
        newSession.setUserDevice(matched.getUserDevice());
        newSession.setRefreshTokenHash(passwordEncoder.encode(UUID.randomUUID().toString()));
        newSession.setExpiresTimestamp(LocalDateTime.now().plusDays(30));
        newSession.setRevoked(false);

        userSessionRepository.save(newSession);

        // Check Admin Status for refresh
        // Check Admin Status for refresh
        boolean isAdmin = false;
        String adminToken = null;
        try {
            log.error("DEBUG_ADMIN_CHECK: Refresh - Checking admin status for email: '{}'",
                    matched.getUser().getEmail());
            java.util.Optional<Admin> adminOpt = adminRepository.findByEmail(matched.getUser().getEmail());
            if (adminOpt.isPresent() && Boolean.TRUE.equals(adminOpt.get().getIsActive())) {
                String potentialToken = jwtService.generateAdminToken(adminOpt.get().getAdminId(),
                        adminOpt.get().getRole());
                if (potentialToken != null) {
                    adminToken = potentialToken;
                    isAdmin = true;
                    log.error("DEBUG_ADMIN_CHECK: Refresh - Admin token generated! isAdmin=true");
                }
            } else {
                log.error("DEBUG_ADMIN_CHECK: Refresh - No Active Admin found");
            }
        } catch (Exception e) {
            log.error("Failed to check admin status for user refresh: {}", matched.getUser().getEmail(), e);
        }

        return new LoginResponse(
                jwtService.generateAccessToken(matched.getUser(), newSession.getSessionId()),
                null,
                matched.getUser().getId(),
                matched.getUser().getName(),
                matched.getUser().getEmail(),
                matched.getUser().getPhone(),
                matched.getUser().isEmailVerified(),
                matched.getUser().isPhoneVerified(),
                matched.getUser().getStatus().getName(),
                isAdmin,
                adminToken);
    }

    /* ================= LOGOUT ================= */
    @Transactional
    public void logout(LogoutRequest request, Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new ApiException("SESSION_EXPIRED");
        }

        User user = (User) authentication.getPrincipal();
        List<UserSession> sessions = userSessionRepository.findActiveSessions(user.getId());

        for (UserSession session : sessions) {
            if (passwordEncoder.matches(request.getRefreshToken(), session.getRefreshTokenHash())) {
                session.setRevoked(true);
                userSessionRepository.save(session);
                return;
            }
        }

        throw new ApiException("INVALID_REFRESH_TOKEN");
    }

    @Transactional
    public void logoutAllDevices(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new ApiException("SESSION_EXPIRED");
        }

        User user = (User) authentication.getPrincipal();
        userSessionRepository.revokeAllSessions(user.getId());
    }

    /* ================= DEVICE ================= */

    /* ================= PASSWORD RESET ================= */
    @Transactional
    public void sendForgotPasswordOtp(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ApiException("USER_NOT_FOUND"));

        otpRepository.invalidateOldOtps(user.getId(), OtpVerification.OtpType.PASSWORD_RESET);

        String otp = otpService.generateOtp();
        OtpVerification entity = new OtpVerification();
        entity.setUser(user);
        entity.setTarget(email);
        entity.setOtpCode(otp);
        entity.setOtpType(OtpVerification.OtpType.PASSWORD_RESET);
        entity.setExpiresAt(LocalDateTime.now().plusMinutes(10));

        otpRepository.save(entity);
        emailService.sendOtpEmail(email, otp);
    }

    @Transactional
    public String verifyPasswordResetOtp(String email, String otpInput) {
        OtpVerification otp = otpRepository
                .findTopByTargetAndOtpTypeAndVerifiedFalseOrderByOtpVerificationIdDesc(
                        email, OtpVerification.OtpType.PASSWORD_RESET)
                .orElseThrow(() -> new ApiException("INVALID_OTP"));

        otpService.validateOtp(otp, otpInput);

        otp.setVerified(true);
        otpRepository.save(otp);

        User user = otp.getUser();
        String resetToken = UUID.randomUUID().toString();
        user.setResetToken(resetToken);
        user.setResetTokenExpiry(LocalDateTime.now().plusMinutes(10));
        userRepository.save(user);

        return resetToken;
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        User user = userRepository.findByResetToken(request.getResetToken())
                .orElseThrow(() -> new ApiException("INVALID_RESET_TOKEN"));

        if (user.getResetTokenExpiry().isBefore(LocalDateTime.now())) {
            throw new ApiException("RESET_TOKEN_EXPIRED");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setResetToken(null);
        user.setResetTokenExpiry(null);

        userRepository.save(user);
    }

    /* ================= PHONE OTP ================= */
    @Transactional
    public void sendPhoneOtp(String phone) {
        String normalizedPhone = PhoneUtil.normalizeIndianPhone(phone);
        User user = userRepository.findByPhone(normalizedPhone)
                .orElseThrow(() -> new ApiException("USER_NOT_FOUND"));

        if (user.isPhoneVerified())
            throw new ApiException("PHONE_ALREADY_VERIFIED");

        otpRepository.invalidateOldOtps(user.getId(), OtpVerification.OtpType.PHONE_VERIFICATION);

        String otp = otpService.generateOtp();
        OtpVerification entity = new OtpVerification();
        entity.setUser(user);
        entity.setTarget(normalizedPhone);
        entity.setOtpCode(otp);
        entity.setOtpType(OtpVerification.OtpType.PHONE_VERIFICATION);
        entity.setExpiresAt(LocalDateTime.now().plusMinutes(10));

        otpRepository.save(entity);
        smsService.sendOtp(normalizedPhone, otp);
    }

    @Transactional
    public LoginResponse verifyNewDeviceOtp(
            VerifyNewDeviceRequest request,
            HttpServletRequest httpRequest) {

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ApiException("USER_NOT_FOUND"));

        // 1️⃣ Validate OTP (USING EXISTING METHOD)
        OtpVerification otp = otpRepository
                .findTopByTargetAndOtpTypeAndVerifiedFalseOrderByOtpVerificationIdDesc(
                        user.getEmail(),
                        OtpVerification.OtpType.NEW_DEVICE_VERIFICATION)
                .orElseThrow(() -> new ApiException("INVALID_OTP"));

        otpService.validateOtp(otp, request.getOtp());
        otp.setVerified(true);
        otpRepository.save(otp);

        // 2️⃣ Trust device (ROBUST: Re-create if missing)
        UserDevice device = userDeviceService.saveOrUpdateUserDevice(user, request.getDeviceContext());

        if (device == null) {
            throw new ApiException("DEVICE_NOT_FOUND");
        }

        device.setDeviceTrusted(true);
        userDeviceRepository.save(device);

        // 4️⃣ Create session + tokens (DO THIS FIRST)
        SessionCreationResult sessionResult = createUserSession(user, device, httpRequest, request.getDeviceContext());

        // 5️⃣ Revoke others (EXCEPT THIS NEW ONE)
        if (request.isLogoutOtherDevices()) {
            userSessionRepository.revokeAllOtherSessions(user.getId(), sessionResult.getSession().getSessionId());
        }

        String accessToken = jwtService.generateAccessToken(user, sessionResult.getSession().getSessionId());

        // CHECK IF USER IS ADMIN
        boolean isAdmin = false;
        String adminToken = null;
        try {
            log.error("DEBUG_ADMIN_CHECK: NewDevice - Checking admin status for email: '{}'", user.getEmail());
            java.util.Optional<Admin> adminOpt = adminRepository.findByEmail(user.getEmail());

            if (adminOpt.isPresent()) {
                log.error("DEBUG_ADMIN_CHECK: NewDevice - Admin found! Active: {}", adminOpt.get().getIsActive());
                if (Boolean.TRUE.equals(adminOpt.get().getIsActive())) {
                    String potentialToken = jwtService.generateAdminToken(adminOpt.get().getAdminId(),
                            adminOpt.get().getRole());
                    if (potentialToken != null) {
                        adminToken = potentialToken;
                        isAdmin = true;
                        log.error("DEBUG_ADMIN_CHECK: NewDevice - Admin token generated! isAdmin=true");
                    }
                }
            } else {
                log.error("DEBUG_ADMIN_CHECK: NewDevice - No Active Admin found");
            }
        } catch (Exception e) {
            log.error("Failed to check admin status for new device: {}", user.getEmail(), e);
        }

        return new LoginResponse(
                accessToken,
                sessionResult.getRawRefreshToken(),
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getPhone(),
                user.isEmailVerified(),
                user.isPhoneVerified(),
                user.getStatus().getName(),
                isAdmin,
                adminToken);
    }

    @Transactional
    public void verifyPhoneOtp(String phone, String otpInput) {
        String normalizedPhone = PhoneUtil.normalizeIndianPhone(phone);

        OtpVerification otp = otpRepository
                .findTopByTargetAndOtpTypeAndVerifiedFalseOrderByOtpVerificationIdDesc(
                        normalizedPhone, OtpVerification.OtpType.PHONE_VERIFICATION)
                .orElseThrow(() -> new ApiException("INVALID_OTP"));

        otpService.validateOtp(otp, otpInput);
        otp.setVerified(true);
        otpRepository.save(otp);

        User user = otp.getUser();
        user.setPhoneVerified(true);
        userRepository.save(user);
    }

    /* ================= EMAIL OTP ================= */
    @Transactional
    public void resendEmailOtp(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ApiException("USER_NOT_FOUND"));

        if (user.isEmailVerified())
            throw new ApiException("EMAIL_ALREADY_VERIFIED");

        otpRepository.invalidateOldOtps(user.getId(), OtpVerification.OtpType.EMAIL_VERIFICATION);

        String otp = otpService.generateOtp();
        OtpVerification otpEntity = new OtpVerification();
        otpEntity.setUser(user);
        otpEntity.setTarget(email);
        otpEntity.setOtpCode(otp);
        otpEntity.setOtpType(OtpVerification.OtpType.EMAIL_VERIFICATION);
        otpEntity.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        otpEntity.setVerified(false);

        otpRepository.save(otpEntity);
        emailService.sendOtpEmail(email, otp);
    }

    @Transactional
    public void verifyEmailOtp(VerifyOtpRequest request) {
        OtpVerification otp = otpRepository
                .findTopByTargetAndOtpTypeAndVerifiedFalseOrderByOtpVerificationIdDesc(
                        request.getEmail(), OtpVerification.OtpType.EMAIL_VERIFICATION)
                .orElseThrow(() -> new ApiException("INVALID_OTP"));

        otpService.validateOtp(otp, request.getOtp());
        otp.setVerified(true);
        otpRepository.save(otp);

        User user = otp.getUser();
        user.setEmailVerified(true);
        userRepository.save(user);
    }

    private boolean userHasNoTrustedDevices(User user) {
        return userDeviceRepository.countTrustedDevices(user.getId()) == 0;
    }

}
