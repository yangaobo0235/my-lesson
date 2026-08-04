package com.yangaobo.dto.ai;

import java.time.LocalDateTime;

public record CourseSummaryAiDTO(
        Long id,
        String title,
        String author,
        String category,
        Double price,
        String cover,
        LocalDateTime updated
) {
}
