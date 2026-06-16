package com.yangaobo.ai.tool.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SearchCoursesRequest(
        @NotBlank
        @Size(max = 100)
        String keyword,

        @Min(1)
        @Max(10)
        Integer limit
) {
}
