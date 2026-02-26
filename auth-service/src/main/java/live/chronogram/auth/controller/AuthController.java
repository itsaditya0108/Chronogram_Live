package live.chronogram.auth.controller;

import live.chronogram.auth.dto.LoginRequest;
import live.chronogram.auth.dto.OtpRequest;
import live.chronogram.auth.dto.TokenResponse;
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
     * Initiates the registration process by sending an OTP to a new mobile number.
     * 
     * @param request Contains the mobileNumber to register.
     * @return Success message if the OTP was generated and sent.
     */
    @PostMapping("/register/send-otp")
    public ResponseEntity<?> sendRegistrationOtp(@RequestBody OtpRequest request) {
        if (request.getMobileNumber() == null || request.getMobileNumber().isEmpty()) {
            throw new RuntimeException("Mobile number is required");
        }
        String otp = authService.sendOtp(request.getMobileNumber(), false);
        logger.info("Registration OTP sent to mobile: {}", request.getMobileNumber());
        return ResponseEntity.ok(java.util.Map.of(
                "message", "OTP sent successfully.",
                "test_otp", otp // TODO: Remove before production
        ));
    }

    /**
     * API Endpoint: POST /api/auth/login/send-otp
     * Initiates the login process by sending an OTP to a registered mobile number.
     * 
     * @param request Contains the registered mobileNumber.
     * @return Success message if the OTP was generated and sent.
     */
    @PostMapping("/login/send-otp")
    public ResponseEntity<?> sendLoginOtp(@RequestBody OtpRequest request) {
        if (request.getMobileNumber() == null || request.getMobileNumber().isEmpty()) {
            throw new RuntimeException("Mobile number is required");
        }
        String otp = authService.sendOtp(request.getMobileNumber(), true);
        logger.info("Login OTP sent to mobile: {}", request.getMobileNumber());
        return ResponseEntity.ok(java.util.Map.of(
                "message", "OTP sent successfully.",
                "test_otp", otp // TODO: Remove before production
        ));
    }

    @PostMapping("/send-email-otp")
    public ResponseEntity<?> sendEmailOtp(@RequestBody OtpRequest request) {
        String otp = "";
        if (request.getRegistrationToken() != null && !request.getRegistrationToken().isEmpty()) {
            if (request.getEmail() == null || request.getEmail().isEmpty()) {
                throw new RuntimeException("Email is required for registration.");
            }
            otp = authService.sendEmailOtp(request.getEmail(), request.getRegistrationToken());
        } else {
            if (request.getMobileNumber() == null || request.getMobileNumber().isEmpty()) {
                throw new RuntimeException("Mobile number is required");
            }
            otp = authService.sendEmailOtp(request.getMobileNumber());
        }
        logger.info("Email OTP sent to: {}",
                (request.getEmail() != null ? request.getEmail() : request.getMobileNumber()));
        return ResponseEntity.ok(java.util.Map.of(
                "message", "Email OTP sent successfully.",
                "test_otp", otp // TODO: Remove before production
        ));
    }

    @PostMapping("/register/resend-otp")
    public ResponseEntity<?> resendRegistrationOtp(@RequestBody OtpRequest request) {
        String otp = "";
        if (request.getEmail() != null && !request.getEmail().trim().isEmpty()) {
            if (request.getRegistrationToken() != null && !request.getRegistrationToken().isEmpty()) {
                otp = authService.sendEmailOtp(request.getEmail(), request.getRegistrationToken());
            } else {
                otp = authService.resendEmailOtpByEmail(request.getEmail());
            }
            logger.info("Email OTP resent to: {}", request.getEmail());
            return ResponseEntity.ok(java.util.Map.of(
                    "message", "Email OTP resent successfully.",
                    "test_otp", otp // TODO: Remove before production
            ));
        } else if (request.getMobileNumber() != null && !request.getMobileNumber().trim().isEmpty()) {
            otp = authService.sendOtp(request.getMobileNumber(), false);
            logger.info("Registration Mobile OTP resent to: {}", request.getMobileNumber());
            return ResponseEntity.ok(java.util.Map.of(
                    "message", "Mobile OTP resent successfully.",
                    "test_otp", otp // TODO: Remove before production
            ));
        } else {
            throw new RuntimeException("Either email or mobileNumber is required for resend-otp");
        }
    }

    @PostMapping("/login/resend-otp")
    public ResponseEntity<?> resendLoginOtp(@RequestBody OtpRequest request) {
        if (request.getMobileNumber() != null && !request.getMobileNumber().trim().isEmpty()) {
            String otp = authService.sendOtp(request.getMobileNumber(), true);
            logger.info("Login Mobile OTP resent to: {}", request.getMobileNumber());
            return ResponseEntity.ok(java.util.Map.of(
                    "message", "Mobile OTP resent successfully.",
                    "test_otp", otp // TODO: Remove before production
            ));
        } else {
            throw new RuntimeException("mobileNumber is required for login resend-otp");
        }
    }

    @PostMapping("/verify-email-registration-otp")
    public ResponseEntity<?> verifyEmailRegistrationOtp(
            @RequestBody live.chronogram.auth.dto.VerifyEmailRegistrationRequest request) {
        String nextToken = authService.verifyEmailOtpForRegistration(request.getEmail(), request.getOtpCode(),
                request.getRegistrationToken());
        return ResponseEntity.ok(
                new TokenResponse(nextToken, null, "Email verified. Complete profile to finalize registration."));
    }

    /**
     * API Endpoint: POST /api/auth/verify-otp
     * Verifies the Mobile OTP for a NEW user attempting to register.
     * 
     * @param loginRequest Details including mobileNumber, otpCode, and device
     *                     metadata.
     * @param request      The raw HTTP request to extract IP/User-Agent.
     * @return A TokenResponse containing a temporary RegistrationToken to proceed
     *         to Email/Profile steps.
     */
    @PostMapping("/verify-otp")
    public ResponseEntity<?> verifyRegistrationOtp(@RequestBody LoginRequest loginRequest, HttpServletRequest request) {
        String accessToken = authService.verifyOtpForRegistration(
                loginRequest.getMobileNumber(),
                loginRequest.getOtpCode(),
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
                request.getHeader("User-Agent"));

        return ResponseEntity.ok(
                new TokenResponse(accessToken, null, "Mobile verified. Verify Email to proceed."));
    }

    /**
     * API Endpoint: POST /api/auth/verify-login-otp
     * Verifies the Mobile OTP for an EXISTING user attempting to log in.
     * Evaluates device trust and may trigger new device approval workflows if
     * necessary.
     * 
     * @param loginRequest Details including mobileNumber, otpCode, and device
     *                     metadata.
     * @param request      The raw HTTP request to extract IP/User-Agent.
     * @return A TokenResponse containing the final Access/Refresh tokens on
     *         success.
     */
    @PostMapping("/verify-login-otp")
    public ResponseEntity<?> verifyLoginOtp(@RequestBody LoginRequest loginRequest, HttpServletRequest request) {
        String accessToken = authService.verifyOtpForLogin(
                loginRequest.getMobileNumber(),
                loginRequest.getOtpCode(),
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
                request.getHeader("User-Agent"));
        return ResponseEntity.ok(new TokenResponse(accessToken, "SAMPLE_REFRESH_TOKEN", "Login successful."));
    }

    /**
     * API Endpoint: POST /api/auth/refresh-token
     * Exchanges a valid Refresh Token for a new Access Token.
     * 
     * @param refreshToken The user's active refresh token.
     * @return A TokenResponse with the new Access Token.
     */
    @PostMapping("/refresh-token")
    public ResponseEntity<?> refreshToken(@RequestParam String refreshToken) {
        String newAccessToken = authService.refreshToken(refreshToken);
        return ResponseEntity.ok(new TokenResponse(newAccessToken, refreshToken, "Token refreshed successfully.")); // Return
                                                                                                                    // same
                                                                                                                    // refresh
                                                                                                                    // token
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestParam String refreshToken) {
        authService.logout(refreshToken);
        return ResponseEntity.ok("Logged out successfully");
    }

    @PostMapping("/verify-new-device")
    public ResponseEntity<?> verifyNewDevice(@RequestBody live.chronogram.auth.dto.VerifyNewDeviceRequest request,
            HttpServletRequest servletRequest) {
        String accessToken = authService.verifyNewDevice(request, getClientIp(servletRequest),
                servletRequest.getHeader("User-Agent"));
        return ResponseEntity
                .ok(new TokenResponse(accessToken, "SAMPLE_REFRESH_TOKEN", "New device verified and logged in."));
    }

    @PostMapping("/resend-new-device-otp")
    public ResponseEntity<?> resendNewDeviceOtp(@RequestBody OtpRequest request) {
        if (request.getTemporaryToken() == null || request.getTemporaryToken().trim().isEmpty()) {
            throw new RuntimeException("Temporary token is required for resend.");
        }
        String otp = authService.resendNewDeviceOtp(request.getTemporaryToken());
        logger.info("New Device OTP resent successfully using temporary token.");
        return ResponseEntity.ok(java.util.Map.of(
                "message", "New Device OTP resent successfully to registered email.",
                "test_otp", otp // TODO: Remove before production
        ));
    }

    @PostMapping("/link-email")
    public ResponseEntity<?> linkEmail(@RequestBody live.chronogram.auth.dto.LinkEmailRequest request) {
        String otp = authService.linkEmail(request);
        logger.info("Link Email OTP sent to: {}", request.getEmail());
        return ResponseEntity.ok(java.util.Map.of(
                "message", "OTP sent to email.",
                "test_otp", otp // TODO: Remove before production
        ));
    }

    @PostMapping("/verify-email-link")
    public ResponseEntity<?> verifyEmailLink(@RequestBody live.chronogram.auth.dto.VerifyEmailRequest request) {
        String nextToken = authService.verifyLinkEmail(request);
        return ResponseEntity.ok(new TokenResponse(nextToken, null, "Email linked. Complete profile to proceed."));
        // Return
        // token
        // as
        // "accessToken"
        // in
        // response,
        // or just raw string?
        // User expects standard format? Or just simple Map?
        // TokenResponse(accessToken, refreshToken). Here we only have Registration
        // Token.
        // Let's reuse TokenResponse with null refresh token.
    }

    /**
     * API Endpoint: POST /api/auth/complete-profile
     * Finalizes the stateless registration flow by consuming the valid
     * RegistrationToken,
     * demographics (name/dob), and device metadata to officially create the user
     * entity
     * and grant session access.
     * 
     * @param request        Contains the required profile parameters and
     *                       RegistrationToken.
     * @param servletRequest Raw request for IP/User-Agent logging.
     * @return A TokenResponse with final user session Access/Refresh tokens.
     */
    @PostMapping("/complete-profile")
    public ResponseEntity<?> completeProfile(@RequestBody live.chronogram.auth.dto.CompleteProfileRequest request,
            HttpServletRequest servletRequest) {
        String accessToken = authService.completeProfile(request, getClientIp(servletRequest),
                servletRequest.getHeader("User-Agent"));
        return ResponseEntity
                .ok(new TokenResponse(accessToken, "SAMPLE_REFRESH_TOKEN", "Registration complete. Welcome!"));
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(org.springframework.security.core.Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        live.chronogram.auth.dto.UserResponse userResponse = authService.getUserDetails(userId);
        return ResponseEntity.ok(userResponse);
    }
}
