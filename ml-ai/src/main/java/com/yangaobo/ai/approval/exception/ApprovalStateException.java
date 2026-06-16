package com.yangaobo.ai.approval.exception;

public class ApprovalStateException extends RuntimeException {

    private final String code;

    public ApprovalStateException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
