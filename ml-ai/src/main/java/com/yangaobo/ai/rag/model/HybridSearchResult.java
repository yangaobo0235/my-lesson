package com.yangaobo.ai.rag.model;

import java.util.List;

public record HybridSearchResult(
        List<SearchHit> hits,
        double highestEvidenceScore,
        boolean reranked
) {
}
