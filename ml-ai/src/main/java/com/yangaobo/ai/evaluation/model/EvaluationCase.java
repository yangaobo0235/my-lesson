package com.yangaobo.ai.evaluation.model;

import com.fasterxml.jackson.databind.JsonNode;

public record EvaluationCase(
        String id,
        String type,
        String question,
        JsonNode expected
) {
}
