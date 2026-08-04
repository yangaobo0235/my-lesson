package com.yangaobo.dto.ai;

import jakarta.validation.constraints.NotNull;

public record AddCartItemRequest(
        @NotNull Long courseId
) {
}
