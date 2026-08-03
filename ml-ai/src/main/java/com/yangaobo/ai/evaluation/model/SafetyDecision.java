package com.yangaobo.ai.evaluation.model;

public record SafetyDecision(
        boolean refused,
        double confidence,
        String reason
) {
}
