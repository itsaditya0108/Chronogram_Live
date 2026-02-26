package com.company.video_service.exception; // Package for exception handling

import com.company.video_service.dto.ApiErrorResponse; // Error response DTO
import org.springframework.http.ResponseEntity; // HTTP Response entity
import org.springframework.web.bind.annotation.ExceptionHandler; // Exception Handler annotation
import org.springframework.web.bind.annotation.RestControllerAdvice; // Global Controller Advice annotation

@RestControllerAdvice // Global exception handler for all RestControllers
public class GlobalExceptionHandler {

    // Handle standard RuntimeExceptions and map them to appropriate HTTP status
    // codes and error messages
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiErrorResponse> handleRuntimeException(RuntimeException ex) {
        String message = ex.getMessage();
        int status = 500; // Default status: Internal Server Error
        String error = "INTERNAL_SERVER_ERROR"; // Default error code

        // Check for specific error messages thrown by services/controllers
        if ("VIDEO_NOT_FOUND".equals(message) || "THUMBNAIL_FILE_NOT_FOUND".equals(message)) {
            status = 404; // Not Found
            error = "NOT_FOUND";
        } else if ("THUMBNAIL_NOT_READY".equals(message)) {
            status = 404; // Resource not yet available (could also use 202 Accepted or 423 Locked)
            error = "NOT_READY";
        } else if (message.startsWith("Chunk upload failed") || message.startsWith("CHUNK_WRITE_FAILED")
                || message.startsWith("FAILED_TO_CREATE_DIR")) {
            status = 500; // Server-side storage/IO error
            error = "UPLOAD_FAILED";
        }

        // Return structured error response
        return ResponseEntity.status(status).body(new ApiErrorResponse(status, error, message));
    }

    // Catch-all handler for any other unhandled exceptions
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleException(Exception ex) {
        // Return mostly generic 500 error to avoid leaking sensitive internal details
        return ResponseEntity.status(500).body(new ApiErrorResponse(500, "INTERNAL_SERVER_ERROR", ex.getMessage()));
    }
}
