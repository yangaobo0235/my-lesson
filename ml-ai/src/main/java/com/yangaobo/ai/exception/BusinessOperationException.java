package com.yangaobo.ai.exception;

public class BusinessOperationException extends RuntimeException {

    private final String code;

    public BusinessOperationException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
