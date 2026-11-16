package com.yangaobo.servicedesk.gateway.component.web;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * 在公开入口移除伪造身份及外部追踪头，建立本地请求关联标识。
 *
 * @author yangaobo
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GatewayIngressFilter implements WebFilter {

    /**
     * 当前交换中的请求标识属性键。
     */
    public static final String TRACE_ATTRIBUTE = GatewayIngressFilter.class.getName() + ".traceId";

    /**
     * 清理后再交给路由及下游，Cookie仍按浏览器会话合同传递。
     *
     * @param exchange 当前HTTP交换
     * @param chain    后续响应式过滤链
     * @return 完成或失败信号
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String traceId = UUID.randomUUID().toString().replace("-", "");
        exchange.getAttributes().put(TRACE_ATTRIBUTE, traceId);
        exchange.getResponse()
                .beforeCommit(
                        () -> {
                            // 下游已有本地请求标识时保持其响应合同；网关自身响应才补充当前标识。
                            if (!exchange.getResponse().getHeaders().containsKey("X-Trace-Id")) {
                                exchange.getResponse().getHeaders().set("X-Trace-Id", traceId);
                            }
                            return Mono.empty();
                        });
        ServerHttpRequest request =
                exchange.getRequest()
                        .mutate()
                        .headers(
                                headers -> {
                                    List<String> removed =
                                            headers.keySet().stream()
                                                    .filter(GatewayIngressFilter::untrustedHeader)
                                                    .toList();
                                    removed.forEach(headers::remove);
                                })
                        .build();
        return chain.filter(exchange.mutate().request(request).build());
    }

    /**
     * 判断浏览器不得声明的身份、服务认证及追踪头。
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
            case "authorization",
                 "x-organization-id",
                 "x-customer-scopes",
                 "x-session-ref",
                 "x-auth-version",
                 "x-trace-id",
                 "traceparent",
                 "tracestate",
                 "baggage",
                 "b3" -> true;
            default -> false;
        };
    }
}
