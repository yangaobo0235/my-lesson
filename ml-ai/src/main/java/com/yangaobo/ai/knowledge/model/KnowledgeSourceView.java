package com.yangaobo.ai.knowledge.model;

import java.time.Instant;

public record KnowledgeSourceView(
        String sourceType,
        String sourceId,
        String title,
        String sourceUrl,
        long version,
        String indexStatus,
        long chunkCount,
        Instant indexedAt,
        Instant updatedAt,
        String lastEventStatus,
        Integer retryCount,
        String lastError
) {
}
