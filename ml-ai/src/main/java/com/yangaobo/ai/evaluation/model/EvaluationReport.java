package com.yangaobo.ai.evaluation.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record EvaluationReport(
        UUID id,
        String mode,
        String datasetVersion,
        String modelVersion,
        String promptVersion,
        String configHash,
        Instant executedAt,
        int totalCount,
        int passedCount,
        int failedCount,
        long totalTokenUsage,
        long p95LatencyMs,
        boolean thresholdGateActive,
        boolean gatePassed,
        Map<String, Double> metrics,
        List<EvaluationCaseResult> failures,
        List<EvaluationCaseResult> results
) {
}
