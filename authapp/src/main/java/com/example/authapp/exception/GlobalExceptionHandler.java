package com.example.authapp.exception;

import com.example.authapp.dto.ApiErrorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.validation.FieldError;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.dao.CannotAcquireLockException;

@RestControllerAdvice
public class GlobalExceptionHandler {

        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<ApiErrorResponse> handleValidationExceptions(MethodArgumentNotValidException ex) {
                String errorCode = "BAD_REQUEST";
                String errorMessage = "Validation failed";

                if (ex.getBindingResult().hasErrors()) {
                        FieldError error = ex.getBindingResult().getFieldError();
                        if (error != null) {
                                errorCode = error.getDefaultMessage(); // e.g. NAME_REQUIRED
                                errorMessage = "Validation failed: " + errorCode;
                        }
                }

                return ResponseEntity.status(400).body(
                                new ApiErrorResponse(400, errorCode, errorMessage));
        }

        @ExceptionHandler(ApiException.class)
        public ResponseEntity<ApiErrorResponse> handleApiException(ApiException ex) {

                return switch (ex.getCode()) {

                        case "EMAIL_NOT_VERIFIED" ->
                                ResponseEntity.status(403).body(
                                                new ApiErrorResponse(
                                                                403,
                                                                "EMAIL_NOT_VERIFIED",
                                                                "Please verify your email before login"));

                        case "INVALID_CREDENTIALS" ->
                                ResponseEntity.status(401).body(
                                                new ApiErrorResponse(
                                                                401,
                                                                "INVALID_CREDENTIALS",
                                                                "Email or password is incorrect"));

                        case "USER_NOT_FOUND" ->
                                ResponseEntity.status(404).body(
                                                new ApiErrorResponse(
                                                                404,
                                                                "USER_NOT_FOUND",
                                                                "User does not exist"));

                        case "USER_NOT_ACTIVE" ->
                                ResponseEntity.status(403).body(
                                                new ApiErrorResponse(
                                                                403,
                                                                "USER_NOT_ACTIVE",
                                                                "Your account is not active. Please contact support."));

                        case "USER_BLOCKED" ->
                                ResponseEntity.status(403).body(
                                                new ApiErrorResponse(
                                                                403,
                                                                "USER_BLOCKED",
                                                                ex.getMessage())); // Uses the custom message passed in
                                                                                   // exception

                        case "ACCOUNT_LOCKED" ->
                                ResponseEntity.status(423).body(
                                                new ApiErrorResponse(
                                                                423,
                                                                "ACCOUNT_LOCKED",
                                                                "Account locked due to multiple failed login attempts. Try later."));

                        case "EMAIL_EXISTS" ->
                                ResponseEntity.status(409).body(
                                                new ApiErrorResponse(
                                                                409,
                                                                "EMAIL_EXISTS",
                                                                "Email is already registered"));

                        case "PHONE_ALREADY_EXISTS" ->
                                ResponseEntity.status(409).body(
                                                new ApiErrorResponse(
                                                                409,
                                                                "PHONE_ALREADY_EXISTS",
                                                                "Phone number is already registered"));

                        case "EMAIL_ALREADY_VERIFIED" ->
                                ResponseEntity.status(400).body(
                                                new ApiErrorResponse(
                                                                400,
                                                                "EMAIL_ALREADY_VERIFIED",
                                                                "Email is already verified"));

                        case "PHONE_ALREADY_VERIFIED" ->
                                ResponseEntity.status(400).body(
                                                new ApiErrorResponse(
                                                                400,
                                                                "PHONE_ALREADY_VERIFIED",
                                                                "Phone number is already verified"));

                        case "INVALID_OTP" ->
                                ResponseEntity.status(400).body(
                                                new ApiErrorResponse(
                                                                400,
                                                                "INVALID_OTP",
                                                                "Invalid OTP entered"));

                        case "OTP_EXPIRED" ->
                                ResponseEntity.status(400).body(
                                                new ApiErrorResponse(
                                                                400,
                                                                "OTP_EXPIRED",
                                                                "OTP has expired"));

                        case "OTP_LOCKED" ->
                                ResponseEntity.status(429).body(
                                                new ApiErrorResponse(
                                                                429,
                                                                "OTP_LOCKED",
                                                                "Too many attempts. Try later."));

                        case "INVALID_RESET_TOKEN" ->
                                ResponseEntity.status(403).body(
                                                new ApiErrorResponse(
                                                                403,
                                                                "INVALID_RESET_TOKEN",
                                                                "Invalid or expired reset token"));

                        case "RESET_TOKEN_EXPIRED" ->
                                ResponseEntity.status(403).body(
                                                new ApiErrorResponse(
                                                                403,
                                                                "RESET_TOKEN_EXPIRED",
                                                                "Reset token has expired"));

                        case "STATUS_NOT_FOUND" ->
                                ResponseEntity.status(500).body(
                                                new ApiErrorResponse(
                                                                500,
                                                                "STATUS_NOT_FOUND",
                                                                "User status configuration error"));
                        case "INVALID_REFRESH_TOKEN" ->
                                ResponseEntity.status(401).body(
                                                new ApiErrorResponse(
                                                                401,
                                                                "INVALID_REFRESH_TOKEN",
                                                                "Refresh token is invalid"));

                        case "REFRESH_TOKEN_EXPIRED" ->
                                ResponseEntity.status(401).body(
                                                new ApiErrorResponse(
                                                                401,
                                                                "REFRESH_TOKEN_EXPIRED",
                                                                "Refresh token has expired. Please login again."));

                        case "PHONE_NOT_VERIFIED" ->
                                ResponseEntity.status(403).body(
                                                new ApiErrorResponse(
                                                                403,
                                                                "PHONE_NOT_VERIFIED",
                                                                "Please verify your phone number"));

                        case "SESSION_EXPIRED" ->
                                ResponseEntity.status(401).body(
                                                new ApiErrorResponse(
                                                                401,
                                                                "SESSION_EXPIRED",
                                                                "Session expired. Please login again."));

                        case "NEW_DEVICE_VERIFICATION_REQUIRED" ->
                                ResponseEntity.status(403).body(
                                                new ApiErrorResponse(
                                                                403,
                                                                "NEW_DEVICE_VERIFICATION_REQUIRED",
                                                                "New device detected. Please verify with OTP sent to your email."));

                        case "DEVICE_NOT_FOUND" ->
                                ResponseEntity.status(404).body(
                                                new ApiErrorResponse(
                                                                404,
                                                                "DEVICE_NOT_FOUND",
                                                                "Device identification failed."));

                        case "FAILED_TO_SEND_EMAIL" ->
                                ResponseEntity.status(503).body(
                                                new ApiErrorResponse(
                                                                503,
                                                                "FAILED_TO_SEND_EMAIL",
                                                                "Failed to send verification email. Please check your email configuration or try again later."));

                        default ->
                                ResponseEntity.status(400).body(
                                                new ApiErrorResponse(
                                                                400,
                                                                ex.getCode(),
                                                                "Bad request"));
                };
        }

        @ExceptionHandler(Exception.class)
        public ResponseEntity<ApiErrorResponse> handleGeneralException(Exception ex) {
                return ResponseEntity.status(500).body(
                                new ApiErrorResponse(
                                                500,
                                                "INTERNAL_SERVER_ERROR",
                                                ex.getMessage() != null ? ex.getMessage()
                                                                : "An unexpected error occurred"));
        }

        @ExceptionHandler({
                        DataIntegrityViolationException.class,
                        TransactionSystemException.class,
                        CannotAcquireLockException.class
        })
        public ResponseEntity<ApiErrorResponse> handleDatabaseExceptions(Exception ex) {
                String message = "Database error occurred";
                if (ex instanceof CannotAcquireLockException || ex.getMessage().contains("Lock wait timeout")) {
                        message = "The system is busy or a database lock occurred. Please try again in a few moments.";
                } else if (ex instanceof DataIntegrityViolationException) {
                        message = "Data integrity violation. This record might already exist or violates constraints.";
                }

                return ResponseEntity.status(500).body(
                                new ApiErrorResponse(500, "DATABASE_ERROR", message));
        }

        @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
        public ResponseEntity<ApiErrorResponse> handleMaxSizeException(
                        org.springframework.web.multipart.MaxUploadSizeExceededException ex) {
                return ResponseEntity.status(413).body(
                                new ApiErrorResponse(413, "PAYLOAD_TOO_LARGE", "Uploaded file is too large."));
        }
}
