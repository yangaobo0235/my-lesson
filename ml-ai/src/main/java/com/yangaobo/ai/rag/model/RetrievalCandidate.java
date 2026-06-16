package com.yangaobo.ai.rag.model;

public record RetrievalCandidate(
        SearchHit hit,
        String fusionKey,
        double evidenceScore
) {
}
