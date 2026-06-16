package com.yangaobo.ai.tool.model;

import java.util.UUID;

public record ToolCallRecord(
        UUID id,
        String status,
        boolean success,
        String responseJson,
        String errorCode
) {
}
