package com.company.image_service.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(RuntimeException ex) {
        String msg = ex.getMessage();
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;

        if ("FILE_EMPTY".equals(msg) || "NO_IMAGES_SELECTED".equals(msg) || "NO_VALID_IMAGES_PROVIDED".equals(msg)) {
            status = HttpStatus.BAD_REQUEST;
        } else if (msg != null && msg.contains("Unsupported format")) {
            status = HttpStatus.UNSUPPORTED_MEDIA_TYPE; // 415
        } else if (msg != null && msg.contains("Exceeds size limit")) {
            status = HttpStatus.PAYLOAD_TOO_LARGE; // 413
        }

        Map<String, Object> body = new HashMap<>();
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", msg);
        
        return new ResponseEntity<>(body, status);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        body.put("error", HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase());
        body.put("message", "An unexpected error occurred");
        
        return new ResponseEntity<>(body, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
