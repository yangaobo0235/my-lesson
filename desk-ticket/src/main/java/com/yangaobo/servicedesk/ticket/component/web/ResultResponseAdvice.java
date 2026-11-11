package com.yangaobo.servicedesk.ticket.component.web;

import com.yangaobo.servicedesk.common.base.Result;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * 只为现有Result补充请求标识，不包装文件、健康或OpenAPI协议。
 *
 * @author yangaobo
 */
@RestControllerAdvice
public class ResultResponseAdvice implements ResponseBodyAdvice<Object> {

    /**
     * 允许检查实际响应体，由编码时判断是否为Result。
     *
     * @param returnType 控制器返回声明
     * @param converterType 实际编码器
     * @return 始终检查实际响应体
     */
    @Override
    public boolean supports(MethodParameter returnType,
            Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    /**
     * 仅补充Result追踪字段，保留ResponseEntity状态及响应头。
     *
     * @param body 原始响应体
     * @param returnType 返回声明
     * @param selectedContentType 响应媒体类型
     * @param selectedConverterType 编码器类型
     * @param request 当前请求
     * @param response 当前响应
     * @return 原始协议体或补充标识后的Result
     */
    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType,
            MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType,
            ServerHttpRequest request, ServerHttpResponse response) {
        if (body instanceof Result<?> result && request instanceof ServletServerHttpRequest servlet) {
            return result.withTraceId(RequestTraceFilter.traceId(servlet.getServletRequest()));
        }
        return body;
    }
}
