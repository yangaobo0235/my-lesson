package com.yangaobo.ai.client;

public record InternalAiResponse<T>(
        Integer code,
        String message,
        String coderMessage,
        T data
) {

    private static final int SUCCESS_CODE = 1000;

    public boolean successful() {
        return code != null && code == SUCCESS_CODE;
    }
}
