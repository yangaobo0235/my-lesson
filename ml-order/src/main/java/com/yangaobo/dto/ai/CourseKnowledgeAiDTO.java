package com.yangaobo.dto.ai;

import java.time.LocalDateTime;
import java.util.List;

public record CourseKnowledgeAiDTO(
        Long id,
        String title,
        String author,
        String category,
        String description,
        String detail,
        List<String> episodeTitles,
        LocalDateTime updated
) {
}
