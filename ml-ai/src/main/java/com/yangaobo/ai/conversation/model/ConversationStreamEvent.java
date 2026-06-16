package com.yangaobo.ai.conversation.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ConversationStreamEvent(
        String type,
        UUID conversationId,
        UUID runId,
        UUID requestId,
        String traceId,
        Instant timestamp,
        Map<String, Object> data
) {
}
