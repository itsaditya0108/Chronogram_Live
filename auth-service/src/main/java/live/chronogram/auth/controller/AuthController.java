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

    @PostMapping("/send-otp")
    public ResponseEntity<?> sendOtp(@RequestBody OtpRequest request) {
        if (request.getMobileNumber() == null || request.getMobileNumber().isEmpty()) {
            throw new RuntimeException("Mobile number is required");
        }
        authService.sendOtp(request.getMobileNumber());
        logger.info("OTP sent to mobile: {}", request.getMobileNumber());
        return ResponseEntity.ok("OTP sent successfully.");
    }

    @PostMapping("/send-email-otp")
    public ResponseEntity<?> sendEmailOtp(@RequestBody OtpRequest request) {
        if (request.getRegistrationToken() != null && !request.getRegistrationToken().isEmpty()) {
            if (request.getEmail() == null || request.getEmail().isEmpty()) {
                throw new RuntimeException("Email is required for registration.");
            }
            authService.sendEmailOtp(request.getEmail(), request.getRegistrationToken());
        } else {
            if (request.getMobileNumber() == null || request.getMobileNumber().isEmpty()) {
                throw new RuntimeException("Mobile number is required");
            }
            authService.sendEmailOtp(request.getMobileNumber());
        }
        logger.info("Email OTP sent to: {}",
                (request.getEmail() != null ? request.getEmail() : request.getMobileNumber()));
        return ResponseEntity.ok("Email OTP sent successfully.");
    }

    @PostMapping("/resend-otp")
    public ResponseEntity<?> resendOtp(@RequestBody OtpRequest request) {
        if (request.getEmail() != null && !request.getEmail().trim().isEmpty()) {
            if (request.getRegistrationToken() != null && !request.getRegistrationToken().isEmpty()) {
                authService.sendEmailOtp(request.getEmail(), request.getRegistrationToken());
            } else {
                authService.sendEmailOtp(request.getEmail());
            }
            logger.info("Email OTP resent to: {}", request.getEmail());
            return ResponseEntity.ok("Email OTP resent successfully.");
        } else if (request.getMobileNumber() != null && !request.getMobileNumber().trim().isEmpty()) {
            authService.sendOtp(request.getMobileNumber());
            logger.info("Mobile OTP resent to: {}", request.getMobileNumber());
            return ResponseEntity.ok("Mobile OTP resent successfully.");
        } else {
            throw new RuntimeException("Either email or mobileNumber is required for resend-otp");
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

    @PostMapping("/link-email")
    public ResponseEntity<?> linkEmail(@RequestBody live.chronogram.auth.dto.LinkEmailRequest request) {
        authService.linkEmail(request);
        logger.info("Link Email OTP sent to: {}", request.getEmail());
        return ResponseEntity.ok("OTP sent to email.");
    }

    @PostMapping("/verify-email-link")
    public ResponseEntity<?> verifyEmailLink(@RequestBody live.chronogram.auth.dto.VerifyEmailRequest request) {
        String nextToken = authService.verifyLinkEmail(request);
        return ResponseEntity.ok(new TokenResponse(nextToken, null, "Email linked. Complete profile to proceed.")); // Return
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
