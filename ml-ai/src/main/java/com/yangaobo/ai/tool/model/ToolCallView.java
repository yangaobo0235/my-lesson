package com.yangaobo.ai.tool.model;

import java.time.Instant;
import java.util.UUID;

public record ToolCallView(
        UUID id,
        UUID runId,
        Long userId,
        UUID requestId,
        String toolName,
        String toolSource,
        String mcpServerName,
        String externalToolName,
        String accessType,
        String status,
        boolean success,
        Long latencyMs,
        String errorCode,
        String requestHash,
        String responseHash,
        String requestJson,
        String responseJson,
        Instant createdAt,
        Instant finishedAt
) {
}
