package com.yangaobo.ai.workflow.model;

import java.time.Instant;
import java.util.UUID;

public record LearningPlanDraftRecord(
        UUID id,
        UUID runId,
        LearningPlanState state,
        Instant createdAt,
        Instant updatedAt
) {
}
