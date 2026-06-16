package com.yangaobo.ai.conversation.dto;

import java.util.UUID;

public record MessageSubmissionResponse(
        UUID conversationId,
        UUID runId,
        UUID requestId,
        String status,
        UUID userMessageId,
        UUID assistantMessageId,
        String traceId
) {
}
