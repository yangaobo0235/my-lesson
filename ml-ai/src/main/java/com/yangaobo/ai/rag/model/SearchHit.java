package com.yangaobo.ai.rag.model;

public record SearchHit(
        String sourceType,
        String sourceId,
        String title,
        String snippet,
        String sourceUrl,
        double score,
        long version,
        String visibilityStatus
) {

    public SearchHit(
            String sourceType,
            String sourceId,
            String title,
            String snippet,
            String sourceUrl,
            double score) {
        this(sourceType, sourceId, title, snippet, sourceUrl,
                score, 1L, "ACTIVE");
    }

    public SearchHit withScore(double newScore) {
        return new SearchHit(
                sourceType,
                sourceId,
                title,
                snippet,
                sourceUrl,
                newScore,
                version,
                visibilityStatus);
    }
}
