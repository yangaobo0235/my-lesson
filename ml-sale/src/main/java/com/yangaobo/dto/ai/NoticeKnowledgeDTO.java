package com.yangaobo.dto.ai;

import java.time.LocalDateTime;

public record NoticeKnowledgeDTO(
        Long id,
        String content,
        LocalDateTime updated
) {
}
