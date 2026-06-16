package com.yangaobo.ai.tool.service;

import com.yangaobo.ai.client.OrderAiClient;
import com.yangaobo.ai.service.AiBusinessGateway;
import com.yangaobo.ai.tool.config.BusinessToolProperties;
import com.yangaobo.ai.tool.dto.CourseIdRequest;
import com.yangaobo.ai.tool.dto.LimitRequest;
import com.yangaobo.ai.tool.dto.NoArgsRequest;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OrderTools {

    private final AiBusinessGateway businessGateway;
    private final BusinessToolProperties properties;

    public OrderTools(
            AiBusinessGateway businessGateway,
            BusinessToolProperties properties) {
        this.businessGateway = businessGateway;
        this.properties = properties;
    }

    public List<OrderAiClient.OrderSummary> getMyRecentOrders(
            LimitRequest request) {
        int requestedLimit = request.limit() == null
                ? 5
                : request.limit();
        int safeLimit = Math.max(
                1,
                Math.min(
                        requestedLimit,
                        properties.getMaxRecentOrderLimit()));
        return businessGateway.getMyOrders(safeLimit);
    }

    public List<OrderAiClient.CartItem> getMyCart(
            NoArgsRequest request) {
        return businessGateway.getMyCart();
    }

    public OrderAiClient.CartItem addCourseToMyCart(
            CourseIdRequest request) {
        return businessGateway.addMyCartItem(request.courseId());
    }

    public boolean removeCourseFromMyCart(
            CourseIdRequest request) {
        return businessGateway.removeMyCartItem(request.courseId());
    }
}
