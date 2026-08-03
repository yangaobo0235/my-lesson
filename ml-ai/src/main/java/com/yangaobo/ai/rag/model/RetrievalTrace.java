package com.yangaobo.ai.rag.model;

import java.util.Map;
import java.util.UUID;

public record RetrievalTrace(
        UUID id,
        UUID runId,
        String queryHash,
        String rewrittenQueryHash,
        int vectorCandidateCount,
        int keywordCandidateCount,
        int fusedCandidateCount,
        boolean rerankApplied,
        boolean rerankFallback,
        int finalHitCount,
        String noAnswerReason,
        Map<String, Long> latencyBreakdown
) {
}
