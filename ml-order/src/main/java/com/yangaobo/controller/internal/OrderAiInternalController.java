package com.yangaobo.controller.internal;

import com.yangaobo.dto.ai.AddCartItemRequest;
import com.yangaobo.dto.ai.CartItemAiDTO;
import com.yangaobo.dto.ai.OrderAiDTO;
import com.yangaobo.service.ai.OrderAiQueryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/internal/ai/users/{userId}")
public class OrderAiInternalController {

    private final OrderAiQueryService orderAiQueryService;

    public OrderAiInternalController(OrderAiQueryService orderAiQueryService) {
        this.orderAiQueryService = orderAiQueryService;
    }

    @GetMapping("/orders")
    public List<OrderAiDTO> orders(
            @PathVariable("userId") Long userId,
            @RequestParam(name = "limit", defaultValue = "20")
            @Min(1) @Max(100) int limit) {
        return orderAiQueryService.orders(userId, limit);
    }

    @GetMapping("/cart")
    public List<CartItemAiDTO> cart(@PathVariable("userId") Long userId) {
        return orderAiQueryService.cart(userId);
    }

    @PostMapping("/cart/items")
    public CartItemAiDTO addCartItem(
            @PathVariable("userId") Long userId,
            @Valid @RequestBody AddCartItemRequest request) {
        return orderAiQueryService.addCartItem(userId, request.courseId());
    }

    @DeleteMapping("/cart/items/{courseId}")
    public boolean deleteCartItem(
            @PathVariable("userId") Long userId,
            @PathVariable("courseId") Long courseId) {
        return orderAiQueryService.deleteCartItem(userId, courseId);
    }
}
