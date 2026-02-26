package com.example.authapp.exception;

public class ApiException extends RuntimeException {

    private final String code;

    // Optional custom message
    private final String customMessage;

    public ApiException(String code) {
        super(code);
        this.code = code;
        this.customMessage = null;
    }

    public ApiException(String code, String customMessage) {
        super(customMessage != null ? customMessage : code);
        this.code = code;
        this.customMessage = customMessage;
    }

    public String getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return customMessage != null ? customMessage : super.getMessage();
    }
}
