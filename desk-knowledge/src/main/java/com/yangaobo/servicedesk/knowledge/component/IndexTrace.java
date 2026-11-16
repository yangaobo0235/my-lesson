package com.yangaobo.servicedesk.knowledge.component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 索引传输上下文与业务信封分开，原任务重放保持初始链路。
 *
 * @author yangaobo
 */
@Component
@RequiredArgsConstructor
public class IndexTrace {
    /**
     * 应用显式装配的W3C传播器。
     */
    private final Propagator propagator;

    /**
     * 当前应用追踪器。
     */
    private final Tracer tracer;

    /**
     * 安全JSON工厂，不包含正文或服务凭据。
     */
    private final ObjectMapper json;

    /**
     * 只保存实际当前span，不以jobId伪造traceId。
     */
    public JsonNode capture() {
        var span = tracer.currentSpan();
        if (span == null) return null;
        var carrier = new HashMap<String, String>();
        propagator.inject(span.context(), carrier, Map::put);
        var value = json.createObjectNode().put("schema_version", 1);
        for (String name : List.of("traceparent", "tracestate"))
            if (carrier.containsKey(name)) value.put(name, carrier.get(name));
        return value.has("traceparent") ? value : null;
    }
}
