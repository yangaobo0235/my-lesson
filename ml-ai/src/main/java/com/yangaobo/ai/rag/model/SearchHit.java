package com.yangaobo.ai.rag.model;

public record SearchHit(
        String sourceType,
        String sourceId,
        String title,
        String snippet,
        String sourceUrl,
        double score
) {

    public SearchHit withScore(double newScore) {
        return new SearchHit(
                sourceType,
                sourceId,
                title,
                snippet,
                sourceUrl,
                newScore);
    }
}
