package com.yangaobo.ai.tool.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CourseIdRequest(
        @NotNull
        @Positive
        Long courseId
) {
}
