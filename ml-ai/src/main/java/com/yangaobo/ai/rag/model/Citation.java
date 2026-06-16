package com.yangaobo.ai.rag.model;

public record Citation(
        int index,
        String sourceType,
        String sourceId,
        String title,
        String sourceUrl,
        String excerpt
) {
}
