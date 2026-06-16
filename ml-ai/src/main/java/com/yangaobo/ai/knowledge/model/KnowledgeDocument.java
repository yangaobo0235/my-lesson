package com.yangaobo.ai.knowledge.model;

import java.util.Map;

public record KnowledgeDocument(
        String sourceType,
        String sourceId,
        String title,
        String content,
        String sourceUrl,
        long version,
        String contentHash,
        Map<String, Object> metadata
) {
}
