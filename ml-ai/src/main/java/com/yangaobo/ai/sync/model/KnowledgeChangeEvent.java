package com.yangaobo.ai.sync.model;

import java.time.Instant;
import java.util.UUID;

public record KnowledgeChangeEvent(
        UUID eventId,
        String eventType,
        String aggregateId,
        long version,
        Instant occurredAt
) {
}
