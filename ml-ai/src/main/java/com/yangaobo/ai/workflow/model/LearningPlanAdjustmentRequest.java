package com.yangaobo.ai.workflow.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record LearningPlanAdjustmentRequest(
        @NotBlank @Size(max = 500) String adjustment,
        @NotNull UUID requestId
) {
}
