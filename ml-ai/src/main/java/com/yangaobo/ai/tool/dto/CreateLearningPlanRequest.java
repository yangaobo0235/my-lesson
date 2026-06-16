package com.yangaobo.ai.tool.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateLearningPlanRequest(
        @NotBlank
        @Size(max = 500)
        String goal,

        @Min(10)
        @Max(480)
        Integer availableMinutesPerDay
) {
}
