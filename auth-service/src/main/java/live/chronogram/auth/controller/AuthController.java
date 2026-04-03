package live.chronogram.auth.controller;

import live.chronogram.auth.dto.*;
import live.chronogram.auth.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*") // Allow CORS for all origins (Dev mode)
public class AuthController {

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(AuthController.class);

    @Autowired
    private AuthService authService;


    /**
     * Extracts the client's IP address from the request headers or remote address.
     * 
     * @param request The HttpServletRequest.
     * @return The client's IP address.
     */
    private String getClientIp(HttpServletRequest request) {
        String remoteAddr = "";
        if (request != null) {
            remoteAddr = request.getHeader("X-FORWARDED-FOR");
            if (remoteAddr == null || "".equals(remoteAddr)) {
                remoteAddr = request.getRemoteAddr();
            }
        }
        return remoteAddr;
    }

    /**
     * API Endpoint: POST /api/auth/register/send-otp
     * Initiates the registration process for a new user.
     * 
     * @param request Contains the mobileNumber (10 digits) and deviceId.
     * @return Success message, the test OTP (debug), and an otpSessionToken (security).
     */
    @PostMapping("/register/send-otp")
    public ResponseEntity<?> sendRegistrationOtp(@RequestBody OtpRequest request, jakarta.servlet.http.HttpServletRequest servletRequest) {
        // 1. Syntax Validation: Ensure mobile number is 10 digits
        if (request.getMobileNumber() == null || !request.getMobileNumber().trim().matches("^\\d{10}$")) {
            throw new live.chronogram.auth.exception.AuthException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Invalid mobile number format. Must be 10 digits.");
        }
        
        // 2. Security Requirement: Every request must be bound to a unique deviceId
        if (request.getDeviceId() == null || request.getDeviceId().trim().isEmpty()) {
            throw new live.chronogram.auth.exception.AuthException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Device ID is required");
        }

        // 3. Delegation: AuthService handles registration checks and OTP generation
        request.setIpAddress(getClientIp(servletRequest));
        String[] result = authService.sendOtp(request, false);
        logger.info("Registration OTP sent to mobile: {}", request.getMobileNumber());

        return ResponseEntity.ok(java.util.Map.of(
                "message", "OTP sent successfully.",
                "otpSessionToken", result[1])); // Bound to this specific device/session
    }


    @PostMapping("/firebase-login")
    public ResponseEntity<TokenResponse> firebaseLogin(@RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        String ipAddress = servletRequest.getRemoteAddr();
        String userAgent = servletRequest.getHeader("User-Agent");
        
        TokenResponse response = authService.authenticateWithFirebase(
                request.getFirebaseIdToken(), 
                request, 
                ipAddress, 
                userAgent
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/firebase-register")
    public ResponseEntity<TokenResponse> firebaseRegister(@RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        return firebaseLogin(request, servletRequest);
    }
    /**
     * API Endpoint: POST /api/auth/login/send-otp
     * Initiates the login process for a registered user.
     * 
     * @param request Contains the mobileNumber and deviceId.
     * @return Success message, test OTP, and session token.
     */
    @PostMapping("/login/send-otp")
    public ResponseEntity<?> sendLoginOtp(@RequestBody OtpRequest request, jakarta.servlet.http.HttpServletRequest servletRequest) {
        // 1. Validation
        if (request.getMobileNumber() == null || !request.getMobileNumber().trim().matches("^\\d{10}$")) {
            throw new live.chronogram.auth.exception.AuthException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Invalid mobile number format. Must be 10 digits.");
        }
        if (request.getDeviceId() == null || request.getDeviceId().trim().isEmpty()) {
            throw new live.chronogram.auth.exception.AuthException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Device ID is required");
        }

        // 2. Logic: AuthService checks for user existence and then issues OTP
        request.setIpAddress(getClientIp(servletRequest));
        String[] result = authService.sendOtp(request, true);
        logger.info("Login OTP sent to mobile: {}", request.getMobileNumber());

        return ResponseEntity.ok(java.util.Map.of(
                "message", "OTP sent successfully.",
                "otpSessionToken", result[1]));
    }

    /**
     * API Endpoint: POST /api/auth/send-email-otp
     * Sends an OTP to the user's email address during registration or linking.
     * 
     * @param request Contains email and/or registration token.
     * @return Success message and test OTP.
     */
    @PostMapping("/send-email-otp")
    public ResponseEntity<?> sendEmailOtp(@RequestBody OtpRequest request) {
        String testOtp = "";
        String accessToken = null;

        // 1. Distinction: Is this a registration step or a direct email link request?
        if (request.getRegistrationToken() != null && !request.getRegistrationToken().isEmpty()) {
            // Case A: REGISTRATION Flow. Email is provided with a valid mobile-verified token.
            if (request.getEmail() == null || request.getEmail().isEmpty()) {
                throw new live.chronogram.auth.exception.AuthException(org.springframework.http.HttpStatus.BAD_REQUEST,
                        "Email is required for registration.");
            }
            // Return BOTH the OTP (for testing) and a new token with email-ready state
            String[] response = authService.sendEmailOtp(request.getEmail(),
                    request.getRegistrationToken());
            testOtp = response[0];
            accessToken = response[1];
        } else {
            // Case B: Direct OTP by mobile (e.g. recovery or legacy flows)
            if (request.getMobileNumber() == null || request.getMobileNumber().isEmpty()) {
                throw new live.chronogram.auth.exception.AuthException(org.springframework.http.HttpStatus.BAD_REQUEST,
                        "Mobile number is required");
            }
            testOtp = authService.sendEmailOtp(request.getMobileNumber());
        }

        logger.info("Email OTP sent to: {}",
                (request.getEmail() != null ? request.getEmail() : request.getMobileNumber()));

        java.util.Map<String, String> responseBody = new java.util.HashMap<>();
        responseBody.put("message", "Email OTP sent successfully.");
        if (accessToken != null) {
            // This token is needed for the 'verify-email-registration-otp' step
            responseBody.put("accessToken", accessToken);
        }

        return ResponseEntity.ok(responseBody);
    }

    /**
     * API Endpoint: POST /api/auth/register/resend-otp
     * Resends an OTP to the user's mobile or email during registration.
     */
    @PostMapping("/register/resend-otp")
    public ResponseEntity<?> resendRegistrationOtp(@RequestBody OtpRequest request) {
        // Condition: Resend can be for Email OR Mobile during registration steps
        if (request.getEmail() != null && !request.getEmail().trim().isEmpty()) {
            // 1. Resend Email OTP
            String testOtp = "";
            String accessToken = null;
            if (request.getRegistrationToken() != null && !request.getRegistrationToken().isEmpty()) {
                String[] response = authService.sendEmailOtp(request.getEmail(),
                        request.getRegistrationToken());
                testOtp = response[0];
                accessToken = response[1];
            } else {
                testOtp = authService.resendEmailOtpByEmail(request.getEmail());
            }
            logger.info("Email OTP resent to: {}", request.getEmail());
            java.util.Map<String, String> responseBody = new java.util.HashMap<>();
            responseBody.put("message", "Email OTP resent successfully.");
            if (accessToken != null) {
                responseBody.put("accessToken", accessToken);
            }
            return ResponseEntity.ok(responseBody);
        } else if (request.getMobileNumber() != null && !request.getMobileNumber().trim().isEmpty()) {
            // 2. Resend Mobile OTP
            if (!request.getMobileNumber().trim().matches("^\\d{10}$")) {
                throw new live.chronogram.auth.exception.AuthException(org.springframework.http.HttpStatus.BAD_REQUEST,
                        "Invalid mobile number format. Must be 10 digits.");
            }
            if (request.getDeviceId() == null || request.getDeviceId().trim().isEmpty()) {
                throw new live.chronogram.auth.exception.AuthException(org.springframework.http.HttpStatus.BAD_REQUEST,
                        "Device ID is required");
            }
            String[] result = authService.resendOtp(request.getMobileNumber(), false, request.getDeviceId());
            logger.info("Registration Mobile OTP resent to: {}", request.getMobileNumber());
            return ResponseEntity.ok(java.util.Map.of(
                    "message", "Mobile OTP resent successfully.",
                    "otpSessionToken", result[1]));
        } else {
            throw new live.chronogram.auth.exception.AuthException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Either email or mobileNumber is required for resend-otp");
        }
    }

    /**
     * API Endpoint: POST /api/auth/login/resend-otp
     * Resends the login OTP to the registered mobile number (bypass spam checks).
     */
    @PostMapping("/login/resend-otp")
    public ResponseEntity<?> resendLoginOtp(@RequestBody OtpRequest request) {
        if (request.getMobileNumber() != null && !request.getMobileNumber().trim().isEmpty()) {
            // Validation
            if (!request.getMobileNumber().trim().matches("^\\d{10}$")) {
                throw new live.chronogram.auth.exception.AuthException(org.springframework.http.HttpStatus.BAD_REQUEST,
                        "Invalid mobile number format. Must be 10 digits.");
            }
            if (request.getDeviceId() == null || request.getDeviceId().trim().isEmpty()) {
                throw new live.chronogram.auth.exception.AuthException(org.springframework.http.HttpStatus.BAD_REQUEST,
                        "Device ID is required");
            }
            // Logic: Issue fresh OTP while preserving sessionID if possible
            String[] result = authService.resendOtp(request.getMobileNumber(), true, request.getDeviceId());
            logger.info("Login Mobile OTP resent to: {}", request.getMobileNumber());
 
            return ResponseEntity.ok(java.util.Map.of(
                    "message", "Mobile OTP resent successfully.",
                    "otpSessionToken", result[1]));
        } else {
            throw new live.chronogram.auth.exception.AuthException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "mobileNumber is required for login resend-otp");
        }
    }

    /**
     * API Endpoint: POST /api/auth/verify-email-registration-otp
     * Verifies the email OTP during the registration process.
     */
    @PostMapping("/verify-email-registration-otp")
    public ResponseEntity<?> verifyEmailRegistrationOtp(
            @RequestBody live.chronogram.auth.dto.VerifyEmailRegistrationRequest request) {

        if (request.getOtpCode() == null || !request.getOtpCode().trim().matches("^\\d{6}$")) {
            throw new live.chronogram.auth.exception.AuthException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "OTP must be exactly 6 digits.");
        }

        return ResponseEntity.ok(authService.verifyEmailOtpForRegistration(request.getEmail(), request.getOtpCode(),
                request.getRegistrationToken()));
    }

    /**
     * API Endpoint: POST /api/auth/verify-otp
     * Verifies the Mobile OTP for a NEW user attempting to register.
     * Extends security by tracking device metadata and client network fingerprints.
     * 
     * @param loginRequest Details including mobileNumber, otpCode, and complete device profile.
     * @param request      Used to extract IP and OS info for audit logging.
     * @return A TokenResponse with a stateless RegistrationToken for the next steps.
     */
    @PostMapping("/verify-otp")
    public ResponseEntity<?> verifyRegistrationOtp(@RequestBody LoginRequest loginRequest, HttpServletRequest request) {
        // 1. Mandatory format validation
        if (loginRequest.getOtpCode() == null || !loginRequest.getOtpCode().trim().matches("^\\d{6}$")) {
            throw new live.chronogram.auth.exception.AuthException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "OTP must be exactly 6 digits.");
        }

        // 2. Transaction: Verify and return the intermediate token
        return ResponseEntity.ok(authService.verifyOtpForRegistration(
                loginRequest.getMobileNumber(),
                loginRequest.getOtpCode(),
                loginRequest.getOtpSessionToken(),
                loginRequest.getEmailOtpCode(),
                loginRequest.getDeviceId(),
                loginRequest.getSimSerial(),
                loginRequest.getPushToken(),
                loginRequest.getDeviceName(),
                loginRequest.getDeviceModel(),
                loginRequest.getOsName(),
                loginRequest.getOsVersion(),
                loginRequest.getAppVersion(),
                loginRequest.getLatitude(),
                loginRequest.getLongitude(),
                loginRequest.getCountry(),
                loginRequest.getCity(),
                getClientIp(request),
                request.getHeader("User-Agent")));
    }

    /**
     * API Endpoint: POST /api/auth/verify-login-otp
     * Verifies the Mobile OTP for an EXISTING user.
     * This endpoint manages transition from OTP verification to JWT issuance, 
     * handling untrusted device scenarios.
     * 
     * @param loginRequest Contains mobile, otp, and device identity.
     * @param request      Used for IP/UA logging.
     * @return Final Access/Refresh tokens OR specialized exceptions for device approval.
     */
    @PostMapping("/verify-login-otp")
    public ResponseEntity<?> verifyLoginOtp(@RequestBody LoginRequest loginRequest, HttpServletRequest request) {
        // 1. Validation
        if (loginRequest.getOtpCode() == null || !loginRequest.getOtpCode().trim().matches("^\\d{6}$")) {
            throw new live.chronogram.auth.exception.AuthException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "OTP must be exactly 6 digits.");
        }

        // 2. Logic: Core authentication flow
        return ResponseEntity.ok(authService.verifyOtpForLogin(
                loginRequest.getMobileNumber(),
                loginRequest.getOtpCode(),
                loginRequest.getOtpSessionToken(),
                loginRequest.getEmailOtpCode(),
                loginRequest.isRecoveryFlow(),
                loginRequest.getDeviceId(),
                loginRequest.getSimSerial(),
                loginRequest.getPushToken(),
                loginRequest.getDeviceName(),
                loginRequest.getDeviceModel(),
                loginRequest.getOsName(),
                loginRequest.getOsVersion(),
                loginRequest.getAppVersion(),
                loginRequest.getLatitude(),
                loginRequest.getLongitude(),
                loginRequest.getCountry(),
                loginRequest.getCity(),
                getClientIp(request),
                request.getHeader("User-Agent")));
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<?> refreshToken(@RequestParam String refreshToken) {
        // 1. Logic: Identity preservation. Returns a fresh ACCESS token using the existing REFRESH token.
        String newAccessToken = authService.refreshToken(refreshToken);
        
        // 2. Consistent Return: Flutter developers expect the same format as login
        return ResponseEntity.ok(new TokenResponse(newAccessToken, refreshToken, "Token refreshed successfully."));
    }

    /**
     * API Endpoint: GET /api/auth/validate-session
     * Lightweight endpoint used by internal microservices (like image-service)
     * to check if a JWT access token is still valid (not revoked).
     */
    @GetMapping("/validate-session")
    public ResponseEntity<?> validateSession(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED).build();
        }
        String token = authHeader.substring(7);
        authService.validateSession(token);
        return ResponseEntity.ok().build();
    }

    /**
     * API Endpoint: POST /api/auth/logout
     * Invalidates the user session by revoking the refresh token in the DB.
     * 
     * @param refreshToken The token to invalidate.
     * @return Success message.
     */
    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestParam String refreshToken) {
        authService.logout(refreshToken);
        return ResponseEntity.ok("Logged out successfully");
    }

    /**
     * API Endpoint: POST /api/auth/verify-new-device
     * Completes the login flow for an untrusted device by verifying a secondary Email OTP.
     */
    @PostMapping("/verify-new-device")
    public ResponseEntity<?> verifyNewDevice(@RequestBody VerifyNewDeviceRequest request,
            HttpServletRequest servletRequest) {

        // 1. Validation
        if (request.getOtp() == null || !request.getOtp().trim().matches("^\\d{6}$")) {
            throw new live.chronogram.auth.exception.AuthException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "OTP must be exactly 6 digits.");
        }

        // 2. Transaction: Mark device as trusted and return JWTs
        return ResponseEntity.ok(authService.verifyNewDevice(request, getClientIp(servletRequest),
                servletRequest.getHeader("User-Agent")));
    }

    /**
     * API Endpoint: POST /api/auth/resend-new-device-otp
     * Resends the verification OTP for a new/untrusted device.
     * 
     * @param request Contains the 'temporaryToken' issued during the initial login attempt.
     * @return Success message and the new temporary token (for session continuity).
     */
    @PostMapping("/resend-new-device-otp")
    public ResponseEntity<?> resendNewDeviceOtp(@RequestBody OtpRequest request) {
        // 1. Validation: The temporaryToken is used to identify the pending login session
        if (request.getTemporaryToken() == null || request.getTemporaryToken().trim().isEmpty()) {
            throw new live.chronogram.auth.exception.AuthException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Temporary token is required for resend.");
        }

        // 2. Logic: Generates a fresh OTP and a new temporary session token
        String[] result = authService.resendNewDeviceOtp(request.getTemporaryToken());
        String otp = result[0];
        String newToken = result[1];

        logger.info("New Device OTP resent successfully using temporary token.");

        return ResponseEntity.ok(java.util.Map.of(
                "message", "New Device OTP resent successfully to registered email.",
                "temporaryToken", newToken));
    }

    /**
     * API Endpoint: POST /api/auth/link-email
     * Initiates the linking of an email address to an account that only has a mobile number.
     * 
     * @param request Includes the email to be linked.
     * @return Success message and a registration token to track the linking state.
     */
    @PostMapping("/link-email")
    public ResponseEntity<?> linkEmail(@RequestBody live.chronogram.auth.dto.LinkEmailRequest request) {
        // 1. Logic: Security checks for email uniqueness and OTP dispatch
        String[] result = authService.linkEmail(request);
        String otp = result[0];
        String token = result[1];

        logger.info("Link Email OTP sent to: {}", request.getEmail());

        java.util.Map<String, Object> responseBody = new java.util.HashMap<>();
        responseBody.put("message", "OTP sent to email.");
        responseBody.put("registrationToken", token);

        return ResponseEntity.ok(responseBody);
    }

    /**
     * API Endpoint: POST /api/auth/verify-email-link
     * Verifies the OTP sent to the email during the linking process.
     */
    @PostMapping("/verify-email-link")
    public ResponseEntity<?> verifyEmailLink(@RequestBody live.chronogram.auth.dto.VerifyEmailRequest request) {
        // 1. Validation
        if (request.getOtp() == null || !request.getOtp().trim().matches("^\\d{6}$")) {
            throw new live.chronogram.auth.exception.AuthException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "OTP must be exactly 6 digits.");
        }

        // 2. Logic: Updates account state upon successful verification
        String nextToken = authService.verifyLinkEmail(request);

        return ResponseEntity.ok(new TokenResponse(nextToken, null, "Email linked. Complete profile to proceed."));
    }

    /**
     * API Endpoint: POST /api/auth/complete-profile
     * The final step of the multi-step registration flow.
     * Uses a RegistrationToken to verify prior mobile/email verification success.
     * 
     * @param request        Includes 'name', 'dob', and 'registrationToken'.
     * @param servletRequest Used for network audit logging.
     * @return Final Access/Refresh tokens.
     */
    @PostMapping("/complete-profile")
    public ResponseEntity<?> completeProfile(@RequestBody live.chronogram.auth.dto.CompleteProfileRequest request,
            HttpServletRequest servletRequest) {

        // 1. Strict Name Validation: Only alphabets and spaces (e.g., no emojis or numbers)
        if (request.getName() == null || !request.getName().trim().matches("^[a-zA-Z\\s]+$")) {
            throw new live.chronogram.auth.exception.AuthException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Validation Error: Name must contain only alphabetic characters and spaces.");
        }

        // 2. Delegate to final user creation and session start
        return ResponseEntity.ok(authService.completeProfile(request, getClientIp(servletRequest),
                servletRequest.getHeader("User-Agent")));
    }


    /**
     * API Endpoint: GET /api/auth/me
     * Returns the user profile for the currently logged-in session.
     * Uses Spring Security's context to identify the user via their JWT.
     * 
     * @param authentication Injected by Spring Security from the valid JWT.
     * @return User details (name, masked email/mobile, verified status).
     */
    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(org.springframework.security.core.Authentication authentication) {
        // 1. Extract identifying subject (userId) from the token
        Long userId = Long.parseLong(authentication.getName());
        
        // 2. Fetch and return masked profile details
        live.chronogram.auth.dto.UserResponse userResponse = authService.getUserDetails(userId);
        return ResponseEntity.ok(userResponse);
    }

    /**
     * API Endpoint: GET /api/auth/storage/usage
     * Returns the aggregated storage usage for the authenticated user.
     */
    @GetMapping("/storage/usage")
    public ResponseEntity<live.chronogram.auth.dto.StorageUsageResponse> getMyStorageUsage(org.springframework.security.core.Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok(authService.getStorageUsage(userId));
    }
}
