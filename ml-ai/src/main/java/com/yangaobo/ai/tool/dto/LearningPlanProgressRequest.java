package com.yangaobo.ai.tool.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record LearningPlanProgressRequest(
        @Min(0)
        @Max(100)
        int progressPercent,

        @Size(max = 500)
        String note
) {
}
