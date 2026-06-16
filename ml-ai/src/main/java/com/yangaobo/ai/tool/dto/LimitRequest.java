package com.yangaobo.ai.tool.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record LimitRequest(
        @Min(1)
        @Max(20)
        Integer limit
) {
}
