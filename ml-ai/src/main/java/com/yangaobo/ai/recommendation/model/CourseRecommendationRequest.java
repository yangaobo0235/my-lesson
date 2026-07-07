package com.yangaobo.ai.recommendation.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CourseRecommendationRequest(
        @NotBlank
        @Size(max = 200)
        String goal,

        @Min(1)
        @Max(8)
        Integer limit
) {
}
