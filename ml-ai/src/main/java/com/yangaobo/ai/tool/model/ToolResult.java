package com.yangaobo.ai.tool.model;

public record ToolResult<T>(
        boolean success,
        T data,
        String errorCode,
        String message,
        boolean replayed
) {

    public static <T> ToolResult<T> success(T data) {
        return new ToolResult<>(true, data, null, null, false);
    }

    public static <T> ToolResult<T> replayed(T data) {
        return new ToolResult<>(true, data, null, null, true);
    }

    public static <T> ToolResult<T> failure(
            String errorCode,
            String message) {
        return new ToolResult<>(
                false,
                null,
                errorCode,
                message,
                false);
    }

    public ToolResult<T> asReplayed() {
        return new ToolResult<>(
                success,
                data,
                errorCode,
                message,
                true);
    }
}
