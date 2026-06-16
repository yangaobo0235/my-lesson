package com.yangaobo.ai.conversation.model;

import java.util.UUID;

public record ConversationRun(
        UUID id,
        UUID conversationId,
        UUID requestId,
        String status,
        UUID userMessageId,
        UUID assistantMessageId,
        String traceId,
        String errorMessage
) {
}
