package com.yangaobo.servicedesk.ticket.component.web;

import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 复用当前Micrometer span作为请求关联标识，隔离测试关闭追踪时生成本地标识。
 *
 * @author yangaobo
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestTraceFilter extends OncePerRequestFilter {

    /**
     * 请求属性键，异步及错误分派继续读取原请求值。
     */
    public static final String TRACE_ATTRIBUTE = RequestTraceFilter.class.getName() + ".traceId";

    /** 当前应用追踪器，测试关闭追踪时允许不存在。 */
    private final ObjectProvider<Tracer> tracerProvider;

    /**
     * 注入由应用显式启用的追踪器，不创建第二个分布式上下文。
     * @param tracerProvider 可选Micrometer追踪器
     */
    public RequestTraceFilter(ObjectProvider<Tracer> tracerProvider) {
        this.tracerProvider = tracerProvider;
    }

    /**
     * 在业务处理前接续可信内部追踪；公开Gateway负责清理外来头。
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
        Tracer tracer = tracerProvider.getIfAvailable();
        String traceId = tracer != null && tracer.currentSpan() != null
                ? tracer.currentSpan().context().traceId() : UUID.randomUUID().toString().replace("-", "");
        request.setAttribute(TRACE_ATTRIBUTE, traceId);
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
