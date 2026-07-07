package com.yangaobo.dto.ai;

public record InternalAiResponse<T>(
        Integer code,
        String message,
        String coderMessage,
        T data
) {

    public boolean successful() {
        return code != null && code == 1000;
    }
}
