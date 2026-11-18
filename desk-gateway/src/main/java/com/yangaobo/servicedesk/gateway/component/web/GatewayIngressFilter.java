package com.yangaobo.servicedesk.gateway.component.web;

import java.util.UUID;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * 为网关自身响应建立关联标识；请求头由观测之前的入口装饰器统一清理。
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
     * 保存当前交换的兜底标识，不覆盖下游已经提供的响应标识。
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
        return chain.filter(exchange);
    }
}
