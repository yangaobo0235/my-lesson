package com.yangaobo.ai.evaluation.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public record EvaluationResultView(
        UUID id,
        UUID caseId,
        String caseType,
        String question,
        String modelName,
        String answer,
        JsonNode metrics,
        boolean passed,
        Instant createdAt
) {
}
