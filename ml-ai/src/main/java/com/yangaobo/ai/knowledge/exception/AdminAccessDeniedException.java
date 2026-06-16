package com.yangaobo.ai.knowledge.exception;

public class AdminAccessDeniedException extends RuntimeException {

    public AdminAccessDeniedException() {
        super("Administrator role is required");
    }
}
