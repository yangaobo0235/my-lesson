package com.yangaobo.servicedesk.gateway.component.web;

import java.util.List;
import java.util.Locale;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.http.server.reactive.HttpHandlerDecoratorFactory;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;

/**
 * 在WebFlux建立服务端观测之前清理公开请求，防止外部追踪头成为可信父链。
 *
 * @author yangaobo
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GatewayIngressDecorator implements HttpHandlerDecoratorFactory {

    /**
     * 包装框架HTTP处理器，身份、服务认证和追踪头只在该最外层清理一次。
     *
     * @param handler 框架观测与业务过滤链
     * @return 先清理请求再建立观测的处理器
     */
    @Override
    public HttpHandler apply(HttpHandler handler) {
        return (request, response) -> {
            ServerHttpRequest sanitized = request.mutate().headers(headers -> {
                List<String> removed = headers.keySet().stream()
                        .filter(GatewayIngressDecorator::untrustedHeader).toList();
                removed.forEach(headers::remove);
            }).build();
            return handler.handle(sanitized, response);
        };
    }

    /**
     * 判断浏览器不得声明的身份、服务认证及追踪头，保留Cookie和CSRF原协议。
     *
     * @param name 原始头名称
     * @return 是否移除
     */
    private static boolean untrustedHeader(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.startsWith("x-user-")
                || lower.startsWith("x-actor-")
                || lower.startsWith("x-internal-")
                || lower.startsWith("x-b3-")
                || switch (lower) {
            case "authorization", "x-organization-id", "x-customer-scopes",
                 "x-session-ref", "x-auth-version", "x-trace-id", "traceparent",
                 "tracestate", "baggage", "b3" -> true;
            default -> false;
        };
    }
}
