package com.yangaobo.ai.conversation.model;

import com.yangaobo.ai.rag.model.Citation;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ConversationMessage(
        UUID id,
        UUID conversationId,
        String role,
        String content,
        List<Citation> citations,
        UUID requestId,
        String traceId,
        Instant summaryUntil,
        UUID summaryUntilMessageId,
        Instant createdAt
) {
}
