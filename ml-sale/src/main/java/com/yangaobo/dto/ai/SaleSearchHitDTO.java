package com.yangaobo.dto.ai;

import java.time.LocalDateTime;

public record SaleSearchHitDTO(
        String sourceType,
        Long id,
        String title,
        String snippet,
        LocalDateTime updated
) {
}
