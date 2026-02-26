package live.chronogram.auth.exception;

import live.chronogram.auth.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

@ControllerAdvice
public class GlobalExceptionHandler {

        @ExceptionHandler(DeviceApprovalRequiredException.class)
        public ResponseEntity<ErrorResponse> handleDeviceApproval(DeviceApprovalRequiredException ex) {
                ErrorResponse error = new ErrorResponse(
                                HttpStatus.UNAUTHORIZED.value(),
                                "Unauthorized",
                                ex.getMessage(),
                                ex.getMaskedEmail(),
                                ex.getTemporaryToken());
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
        }

        @ExceptionHandler(EmailLinkingRequiredException.class)
        public ResponseEntity<ErrorResponse> handleEmailLinking(EmailLinkingRequiredException ex) {
                ErrorResponse error = new ErrorResponse(
                                HttpStatus.FORBIDDEN.value(),
                                "Forbidden",
                                ex.getMessage());
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
        }

        @ExceptionHandler(RuntimeException.class)
        public ResponseEntity<ErrorResponse> handleRuntimeException(RuntimeException ex) {
                ErrorResponse error = new ErrorResponse(
                                HttpStatus.BAD_REQUEST.value(),
                                "Bad Request",
                                ex.getMessage());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }

        @ExceptionHandler(live.chronogram.auth.exception.AuthException.class)
        public ResponseEntity<ErrorResponse> handleAuthException(live.chronogram.auth.exception.AuthException ex,
                        WebRequest request) {
                ErrorResponse errorDetails = new ErrorResponse(
                                ex.getStatus().value(),
                                ex.getMessage(),
                                request.getDescription(false));
                return new ResponseEntity<>(errorDetails, ex.getStatus());
        }

        // Global Exception Handling
        @ExceptionHandler(Exception.class)
        public ResponseEntity<ErrorResponse> globalExceptionHandler(Exception ex, WebRequest request) {
                ErrorResponse error = new ErrorResponse(
                                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                                "Internal Server Error",
                                "An unexpected error occurred: " + ex.getMessage());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
}
