package com.yangaobo.ai.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    @PostMapping("/internal/ai/users/{userId}/cart/items")
    InternalAiResponse<CartItem> addCartItem(
            @PathVariable Long userId,
            @RequestBody AddCartItemRequest request);

    @DeleteMapping("/internal/ai/users/{userId}/cart/items/{courseId}")
    InternalAiResponse<Boolean> deleteCartItem(
            @PathVariable Long userId,
            @PathVariable Long courseId);

    record AddCartItemRequest(Long courseId) {
    }

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
