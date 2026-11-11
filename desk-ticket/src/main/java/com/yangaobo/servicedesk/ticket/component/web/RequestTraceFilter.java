package com.yangaobo.servicedesk.ticket.component.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 为尚未接入可信分布式追踪的请求建立本地关联标识。
 *
 * @author yangaobo
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestTraceFilter extends OncePerRequestFilter {

    /**
     * 请求属性键，异步及错误分派继续读取原请求值。
     */
    public static final String TRACE_ATTRIBUTE = RequestTraceFilter.class.getName() + ".traceId";

    /**
     * 在业务处理前生成标识，不接受客户端传入的追踪身份。
     *
     * @param request 当前请求
     * @param response 当前响应
     * @param filterChain 后续过滤链
     * @throws ServletException 下游Servlet处理失败
     * @throws IOException 请求或响应I/O失败
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        request.setAttribute(TRACE_ATTRIBUTE, UUID.randomUUID().toString().replace("-", ""));
        response.setHeader("X-Trace-Id", traceId(request));
        filterChain.doFilter(request, response);
    }

    /**
     * 返回原请求标识；独立错误分派缺失时才生成补充标识。
     *
     * @param request 当前请求或错误分派
     * @return 非空本地请求标识
     */
    public static String traceId(HttpServletRequest request) {
        Object value = request.getAttribute(TRACE_ATTRIBUTE);
        if (value instanceof String traceId) {
            return traceId;
        }
        String traceId = UUID.randomUUID().toString().replace("-", "");
        request.setAttribute(TRACE_ATTRIBUTE, traceId);
        return traceId;
    }
}
