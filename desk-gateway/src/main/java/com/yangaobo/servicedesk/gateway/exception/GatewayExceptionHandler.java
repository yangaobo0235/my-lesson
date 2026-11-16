package com.yangaobo.servicedesk.gateway.exception;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangaobo.servicedesk.common.base.CommonErrorCode;
import com.yangaobo.servicedesk.common.base.Result;
import com.yangaobo.servicedesk.common.exception.BusinessException;
import com.yangaobo.servicedesk.gateway.component.web.GatewayIngressFilter;
import com.yangaobo.servicedesk.gateway.component.web.HttpErrorMapping;
import io.netty.channel.ConnectTimeoutException;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.ErrorResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 在Reactive边界编码安全错误，不引入MVC异常处理或默认ObjectMapper。
 *
 * @author yangaobo
 */
@Component
@Order(-2)
public class GatewayExceptionHandler implements ErrorWebExceptionHandler {

    /**
     * 安全运行日志，不记录上游原始消息或请求正文。
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(GatewayExceptionHandler.class);

    /**
     * Boot装配后的JSON编码器。
     */
    private final ObjectMapper objectMapper;

    /**
     * 注入所属应用的JSON配置。
     *
     * @param objectMapper Boot管理的编码器
     */
    public GatewayExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 保留已提交响应；未提交时按已知故障选择状态与安全Result。
     *
     * @param exchange  当前HTTP交换
     * @param exception 当前失败
     * @return 有界响应写入或原始失败信号
     */
    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable exception) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(exception);
        }
        CommonErrorCode error = CommonErrorCode.INTERNAL_ERROR;
        int status = 500;
        if (exception instanceof BusinessException business) {
            error = HttpErrorMapping.registered(business.getErrorCode());
            status = HttpErrorMapping.statusOf(error).value();
        } else if (exception instanceof ErrorResponse framework) {
            status = framework.getStatusCode().value();
            error = HttpErrorMapping.fromStatus(status);
            exchange.getResponse().getHeaders().addAll(framework.getHeaders());
        } else if (hasCause(exception, TimeoutException.class)
                || hasCause(exception, ConnectTimeoutException.class)) {
            error = CommonErrorCode.DEPENDENCY_TIMEOUT;
            status = 504;
        } else if (hasCause(exception, ConnectException.class)
                || hasCause(exception, UnknownHostException.class)) {
            error = CommonErrorCode.SERVICE_UNAVAILABLE;
            status = 503;
        }
        String traceId =
                exchange.getAttributeOrDefault(
                        GatewayIngressFilter.TRACE_ATTRIBUTE,
                        UUID.randomUUID().toString().replace("-", ""));
        if (status == 500) {
            LOGGER.error(
                    "网关处理异常 traceId={} exceptionType={}", traceId, exception.getClass().getName());
        }
        byte[] body;
        try {
            body = objectMapper.writeValueAsBytes(Result.failure(error, traceId));
        } catch (JsonProcessingException encodingFailure) {
            return Mono.error(encodingFailure);
        }
        exchange.getResponse().setRawStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        exchange.getResponse().getHeaders().set("X-Trace-Id", traceId);
        return exchange.getResponse()
                .writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
    }

    /**
     * 只在有界异常链上识别网络失败，不解析异常文本。
     *
     * @param exception 原始失败
     * @param type      目标故障类型
     * @return 是否匹配
     */
    private static boolean hasCause(Throwable exception, Class<? extends Throwable> type) {
        Throwable current = exception;
        for (int depth = 0; current != null && depth < 16; depth++) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
