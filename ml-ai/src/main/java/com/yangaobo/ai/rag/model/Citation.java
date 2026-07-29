package com.yangaobo.ai.rag.model;

public record Citation(
        int index,
        String sourceType,
        String sourceId,
        String title,
        String sourceUrl,
        String excerpt,
        long version,
        String visibilityStatus
) {

    public Citation(
            int index,
            String sourceType,
            String sourceId,
            String title,
            String sourceUrl,
            String excerpt) {
        this(index, sourceType, sourceId, title, sourceUrl,
                excerpt, 1L, "ACTIVE");
    }
}
