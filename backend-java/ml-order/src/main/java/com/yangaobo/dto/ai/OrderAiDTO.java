package com.yangaobo.dto.ai;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderAiDTO(
        Long id,
        String sn,
        BigDecimal totalAmount,
        BigDecimal payAmount,
        Integer payType,
        Integer status,
        String info,
        LocalDateTime created,
        List<OrderItemAiDTO> items
) {
}
