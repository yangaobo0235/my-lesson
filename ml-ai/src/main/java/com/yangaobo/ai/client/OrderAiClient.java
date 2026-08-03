package com.yangaobo.ai.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;
import java.util.List;

@FeignClient(name = "ml-order", contextId = "orderAiClient")
public interface OrderAiClient {

    @GetMapping("/internal/ai/users/{userId}/orders")
    InternalAiResponse<List<OrderSummary>> orders(
            @PathVariable Long userId,
            @RequestParam int limit);

    @GetMapping("/internal/ai/users/{userId}/cart")
    InternalAiResponse<List<CartItem>> cart(@PathVariable Long userId);

    record OrderSummary(
            Long id,
            String sn,
            Double totalAmount,
            Double payAmount,
            Integer payType,
            Integer status,
            String info,
            LocalDateTime created,
            List<OrderItem> items
    ) {
    }

    record OrderItem(
            Long courseId,
            String courseTitle,
            String courseCover,
            Double coursePrice
    ) {
    }

    record CartItem(
            Long id,
            Long courseId,
            String courseTitle,
            String courseCover,
            Double coursePrice,
            LocalDateTime created
    ) {
    }
}
