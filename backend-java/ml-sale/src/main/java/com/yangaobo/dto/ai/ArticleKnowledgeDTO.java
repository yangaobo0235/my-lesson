package com.yangaobo.dto.ai;

import java.time.LocalDateTime;

public record ArticleKnowledgeDTO(
        Long id,
        String title,
        String content,
        LocalDateTime updated
) {
}
