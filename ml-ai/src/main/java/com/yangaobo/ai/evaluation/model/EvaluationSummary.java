package com.yangaobo.ai.evaluation.model;

import java.time.Instant;

public record EvaluationSummary(
        long caseCount,
        long resultCount,
        long passedCount,
        double passRate,
        Double averageLatencyMs,
        Double averageTokenUsage,
        String latestModel,
        Instant latestRunAt
) {
}
