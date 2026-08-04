package com.yangaobo.controller.internal;

import com.yangaobo.dto.ai.CartItemAiDTO;
import com.yangaobo.dto.ai.OrderAiDTO;
import com.yangaobo.security.SecurityContext;
import com.yangaobo.service.ai.OrderAiQueryService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/internal/v1/agent/me")
public class AgentOrderToolController {

    private final OrderAiQueryService queryService;

    public AgentOrderToolController(OrderAiQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/orders")
    public List<OrderAiDTO> orders(
            @RequestParam(defaultValue = "20")
            @Min(1) @Max(100) int limit) {
        return queryService.orders(SecurityContext.requireUserId(), limit);
    }

    @GetMapping("/cart")
    public List<CartItemAiDTO> cart() {
        return queryService.cart(SecurityContext.requireUserId());
    }
}
