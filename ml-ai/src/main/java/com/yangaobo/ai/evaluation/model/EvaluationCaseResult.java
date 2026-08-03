package com.yangaobo.ai.evaluation.model;

import java.util.Map;

public record EvaluationCaseResult(
        String caseId,
        String caseType,
        boolean passed,
        String answer,
        String failureReason,
        long latencyMs,
        long tokenUsage,
        Map<String, Object> metrics
) {
}
