package com.yangaobo.ai.conversation.model;

import java.time.Instant;
import java.util.UUID;

public record Conversation(
        UUID id,
        Long userId,
        String title,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
}
