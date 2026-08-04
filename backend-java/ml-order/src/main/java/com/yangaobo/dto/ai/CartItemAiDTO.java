package com.yangaobo.dto.ai;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CartItemAiDTO(
        Long id,
        Long courseId,
        String courseTitle,
        String courseCover,
        BigDecimal coursePrice,
        LocalDateTime created
) {
}
