package com.example.authapp.controller;

import com.example.authapp.dto.*;
import com.example.authapp.entity.User;
import com.example.authapp.exception.ApiException;
import com.example.authapp.services.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

        private static final Logger log = LoggerFactory.getLogger(AuthController.class);

        private final UserService userService;

        public AuthController(UserService userService) {
                this.userService = userService;
        }

        /* ================= REGISTER ================= */

        @PostMapping("/register")
        public ResponseEntity<RegisterResponse> register(
                        @Valid @RequestBody RegisterRequest request) {

                log.info("API | REGISTER | START | email={}", request.getEmail());

                try {
                        User user = userService.register(request);

                        log.info("API | REGISTER | SUCCESS | userId={}", user.getId());

                        return ResponseEntity.ok(
                                        new RegisterResponse(
                                                        user.getId(),
                                                        user.getName(),
                                                        user.getEmail(),
                                                        user.isEmailVerified(),
                                                        "Registration successful. OTP sent to email."));
                } catch (ApiException e) {
                        throw e; // Let GlobalExceptionHandler handle specific API errors
                } catch (Exception e) {
                        log.error("API | REGISTER | ERROR", e);
                        throw new ApiException("REGISTRATION_FAILED");
                }
        }

        /* ================= LOGIN ================= */

        @PostMapping("/login")
        public ResponseEntity<LoginResponse> login(
                        @Valid @RequestBody LoginRequest request,
                        HttpServletRequest httpRequest,
                        HttpServletResponse httpResponse) {

                log.info("API | LOGIN | START | email={}", request.getEmail());

                LoginResponse response = userService.login(request, httpRequest);

                // ✅ HttpOnly cookie for SSE (browser only)
                ResponseCookie cookie = ResponseCookie.from(
                                "ACCESS_TOKEN",
                                response.getAccessToken())
                                .httpOnly(true)
                                .secure(false) // true in prod HTTPS
                                .path("/")
                                .sameSite("Lax")
                                .maxAge(Duration.ofMinutes(15))
                                .build();

                httpResponse.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

                log.info("API | LOGIN | SUCCESS | userId={}", response.getUserId());

                return ResponseEntity.ok(response);
        }

        /* ================= EMAIL OTP ================= */

        @PostMapping("/verify-email-otp")
        public ResponseEntity<?> verifyEmailOtp(
                        @Valid @RequestBody VerifyOtpRequest request) {

                log.info("API | EMAIL_OTP | VERIFY | email={}", request.getEmail());

                userService.verifyEmailOtp(request);

                log.info("API | EMAIL_OTP | VERIFIED | email={}", request.getEmail());

                return ResponseEntity.ok(
                                Map.of("message", "Email verified successfully"));
        }

        @PostMapping("/resend-otp")
        public ResponseEntity<?> resendOtp(
                        @RequestBody ResendOtpRequest request) {

                if (request.getEmail() != null && !request.getEmail().trim().isEmpty()) {
                        log.info("API | EMAIL_OTP | RESEND | email={}", request.getEmail());
                        userService.resendEmailOtp(request.getEmail());
                        log.info("API | EMAIL_OTP | RESENT | email={}", request.getEmail());
                } else if (request.getPhone() != null && !request.getPhone().trim().isEmpty()) {
                        log.info("API | PHONE_OTP | RESEND | phone={}", request.getPhone());
                        userService.sendPhoneOtp(request.getPhone());
                        log.info("API | PHONE_OTP | RESENT | phone={}", request.getPhone());
                } else {
                        return ResponseEntity.badRequest().body(Map.of("message", "Either email or phone is required"));
                }

                return ResponseEntity.ok(
                                Map.of("message", "OTP resent successfully"));
        }

        /* ================= PASSWORD RESET ================= */

        @PostMapping("/forgot-password")
        public ResponseEntity<?> forgotPassword(
                        @Valid @RequestBody ForgotPasswordRequest request) {

                log.info("API | FORGOT_PASSWORD | START | email={}", request.getEmail());

                userService.sendForgotPasswordOtp(request.getEmail());

                log.info("API | FORGOT_PASSWORD | OTP_SENT | email={}", request.getEmail());

                return ResponseEntity.ok(
                                Map.of("message", "OTP sent to email"));
        }

        @PostMapping("/verify-reset-otp")
        public ResponseEntity<?> verifyResetOtp(
                        @Valid @RequestBody VerifyOtpRequest request) {

                log.info("API | PASSWORD_RESET | OTP_VERIFY | email={}", request.getEmail());

                String resetToken = userService.verifyPasswordResetOtp(
                                request.getEmail(),
                                request.getOtp());

                log.info("API | PASSWORD_RESET | OTP_VERIFIED | email={}", request.getEmail());

                return ResponseEntity.ok(
                                Map.of("resetToken", resetToken));
        }

        @PostMapping("/verify-new-device")
        public ResponseEntity<LoginResponse> verifyNewDevice(
                        @RequestBody VerifyNewDeviceRequest request,
                        HttpServletRequest httpRequest) {
                LoginResponse response = userService.verifyNewDeviceOtp(request, httpRequest);

                return ResponseEntity.ok(response);
        }

        @PostMapping("/reset-password")
        public ResponseEntity<?> resetPassword(
                        @Valid @RequestBody ResetPasswordRequest request) {

                log.info("API | PASSWORD_RESET | START");

                userService.resetPassword(request);

                log.info("API | PASSWORD_RESET | SUCCESS");

                return ResponseEntity.ok(
                                Map.of("message", "Password reset successful"));
        }

        /* ================= PHONE OTP ================= */

        @PostMapping("/phone/send-otp")
        public ResponseEntity<?> sendPhoneOtp(
                        @Valid @RequestBody SendPhoneOtpRequest request) {

                log.info("API | PHONE_OTP | SEND | phone={}", request.getPhone());

                userService.sendPhoneOtp(request.getPhone());

                log.info("API | PHONE_OTP | SENT | phone={}", request.getPhone());

                return ResponseEntity.ok(
                                Map.of("message", "OTP sent to phone"));
        }

        @PostMapping("/phone/verify-otp")
        public ResponseEntity<?> verifyPhoneOtp(
                        @Valid @RequestBody VerifyPhoneOtpRequest request) {

                log.info("API | PHONE_OTP | VERIFY | phone={}", request.getPhone());

                userService.verifyPhoneOtp(
                                request.getPhone(),
                                request.getOtp());

                log.info("API | PHONE_OTP | VERIFIED | phone={}", request.getPhone());

                return ResponseEntity.ok(
                                Map.of("message", "Phone verified successfully"));
        }

        @PostMapping("/phone/resend-otp")
        public ResponseEntity<?> resendPhoneOtp(
                        @Valid @RequestBody SendPhoneOtpRequest request) {

                log.info("API | PHONE_OTP | RESEND | phone={}", request.getPhone());

                userService.sendPhoneOtp(request.getPhone());

                log.info("API | PHONE_OTP | RESENT | phone={}", request.getPhone());

                return ResponseEntity.ok(
                                Map.of("message", "OTP resent to phone successfully"));
        }

        /* ================= TOKEN ================= */

        @PostMapping("/refresh-token")
        public ResponseEntity<LoginResponse> refreshToken(
                        @Valid @RequestBody RefreshTokenRequest request) {

                log.info("API | TOKEN | REFRESH | START");

                LoginResponse response = userService.refreshAccessToken(request);

                log.info("API | TOKEN | REFRESH | SUCCESS | userId={}",
                                response.getUserId());

                return ResponseEntity.ok(response);
        }

        /* ================= LOGOUT ================= */

        @PostMapping("/logout")
        public ResponseEntity<?> logout(
                        @Valid @RequestBody LogoutRequest request,
                        Authentication authentication) {

                User user = (User) authentication.getPrincipal();

                log.info("API | LOGOUT | START | userId={}", user.getId());

                userService.logout(request, authentication);

                log.info("API | LOGOUT | SUCCESS | userId={}", user.getId());

                return ResponseEntity.ok(
                                Map.of("message", "Logged out successfully"));
        }

        @PostMapping("/logout-all")
        public ResponseEntity<?> logoutAll(Authentication authentication) {

                User user = (User) authentication.getPrincipal();

                log.info("API | LOGOUT_ALL | START | userId={}", user.getId());

                userService.logoutAllDevices(authentication);

                log.info("API | LOGOUT_ALL | SUCCESS | userId={}", user.getId());

                return ResponseEntity.ok(
                                Map.of("message", "Logged out from all devices"));
        }

        @GetMapping("/validate-session")
        public ResponseEntity<?> validateSession() {
                return ResponseEntity.ok().build();
        }
}
