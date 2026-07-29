package com.yangaobo.ai.rag.model;

import java.util.List;
import java.util.UUID;

public record HybridSearchResult(
        List<SearchHit> hits,
        double highestEvidenceScore,
        boolean reranked,
        UUID traceId,
        int vectorCandidateCount,
        int keywordCandidateCount,
        int fusedCandidateCount,
        boolean rerankFallback,
        boolean rewritten
) {

    public HybridSearchResult(
            List<SearchHit> hits,
            double highestEvidenceScore,
            boolean reranked) {
        this(hits, highestEvidenceScore, reranked, null,
                0, 0, 0, false, false);
    }
}
