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
        try {
            if (request.getMobileNumber() == null || request.getMobileNumber().isEmpty()) {
                return ResponseEntity.badRequest().body("Mobile number is required");
            }
            authService.sendOtp(request.getMobileNumber());
            logger.info("OTP sent to mobile: {}", request.getMobileNumber());
            return ResponseEntity.ok("OTP sent successfully.");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/send-email-otp")
    public ResponseEntity<?> sendEmailOtp(@RequestBody OtpRequest request) {
        try {
            if (request.getRegistrationToken() != null && !request.getRegistrationToken().isEmpty()) {
                if (request.getEmail() == null || request.getEmail().isEmpty()) {
                    return ResponseEntity.badRequest().body("Email is required for registration.");
                }
                authService.sendEmailOtp(request.getEmail(), request.getRegistrationToken());
            } else {
                if (request.getMobileNumber() == null || request.getMobileNumber().isEmpty()) {
                    return ResponseEntity.badRequest().body("Mobile number is required");
                }
                authService.sendEmailOtp(request.getMobileNumber());
            }
            logger.info("Email OTP sent to: {}",
                    (request.getEmail() != null ? request.getEmail() : request.getMobileNumber()));
            return ResponseEntity.ok("Email OTP sent successfully.");
        } catch (

        Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/verify-email-registration-otp")
    public ResponseEntity<?> verifyEmailRegistrationOtp(
            @RequestBody live.chronogram.auth.dto.VerifyEmailRegistrationRequest request) {
        try {
            String nextToken = authService.verifyEmailOtpForRegistration(request.getEmail(), request.getOtpCode(),
                    request.getRegistrationToken());
            return ResponseEntity.ok(
                    new TokenResponse(nextToken, null, "Email verified. Complete profile to finalize registration."));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Email verification failed: " + e.getMessage());
        }
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<?> verifyRegistrationOtp(@RequestBody LoginRequest loginRequest, HttpServletRequest request) {
        try {
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
                    getClientIp(request));

            return ResponseEntity.ok(
                    new TokenResponse(accessToken, null, "Mobile verified. Verify Email to proceed."));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Registration failed: " + e.getMessage());
        }
    }

    @PostMapping("/verify-login-otp")
    public ResponseEntity<?> verifyLoginOtp(@RequestBody LoginRequest loginRequest, HttpServletRequest request) {
        try {
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
                    getClientIp(request));

            return ResponseEntity.ok(new TokenResponse(accessToken, "SAMPLE_REFRESH_TOKEN", "Login successful."));
        } catch (Exception e) {
            // Check for specific exceptions to return cleaner errors?
            return ResponseEntity.badRequest().body("Login failed: " + e.getMessage());
        }
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<?> refreshToken(@RequestParam String refreshToken) {
        try {
            String newAccessToken = authService.refreshToken(refreshToken);
            return ResponseEntity.ok(new TokenResponse(newAccessToken, refreshToken, "Token refreshed successfully.")); // Return
                                                                                                                        // same
                                                                                                                        // refresh
                                                                                                                        // token
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Refresh failed: " + e.getMessage());
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestParam String refreshToken) {
        try {
            authService.logout(refreshToken);
            return ResponseEntity.ok("Logged out successfully");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Logout failed: " + e.getMessage());
        }
    }

    @PostMapping("/verify-new-device")
    public ResponseEntity<?> verifyNewDevice(@RequestBody live.chronogram.auth.dto.VerifyNewDeviceRequest request,
            HttpServletRequest servletRequest) {
        try {
            String accessToken = authService.verifyNewDevice(request, getClientIp(servletRequest));
            return ResponseEntity
                    .ok(new TokenResponse(accessToken, "SAMPLE_REFRESH_TOKEN", "New device verified and logged in."));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Device verification failed: " + e.getMessage());
        }
    }

    @PostMapping("/link-email")
    public ResponseEntity<?> linkEmail(@RequestBody live.chronogram.auth.dto.LinkEmailRequest request) {
        try {
            authService.linkEmail(request);
            authService.linkEmail(request);
            logger.info("Link Email OTP sent to: {}", request.getEmail());
            return ResponseEntity.ok("OTP sent to email.");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Link email failed: " + e.getMessage());
        }
    }

    @PostMapping("/verify-email-link")
    public ResponseEntity<?> verifyEmailLink(@RequestBody live.chronogram.auth.dto.VerifyEmailRequest request) {
        try {
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
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Email verification failed: " + e.getMessage());
        }
    }

    @PostMapping("/complete-profile")
    public ResponseEntity<?> completeProfile(@RequestBody live.chronogram.auth.dto.CompleteProfileRequest request,
            HttpServletRequest servletRequest) {
        try {
            String accessToken = authService.completeProfile(request, getClientIp(servletRequest));
            return ResponseEntity
                    .ok(new TokenResponse(accessToken, "SAMPLE_REFRESH_TOKEN", "Registration complete. Welcome!"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Profile update failed: " + e.getMessage());
        }
    }
}
