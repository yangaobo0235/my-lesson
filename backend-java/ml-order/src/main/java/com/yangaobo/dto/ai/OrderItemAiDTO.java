package com.yangaobo.dto.ai;

import java.math.BigDecimal;

public record OrderItemAiDTO(
        Long courseId,
        String courseTitle,
        String courseCover,
        BigDecimal coursePrice
) {
}
