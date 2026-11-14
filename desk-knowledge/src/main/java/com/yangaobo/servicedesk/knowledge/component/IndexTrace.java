package com.yangaobo.servicedesk.knowledge.component;

/**
 * 索引传输上下文与业务信封分开，原任务重放保持初始链路。
 *
 * @author yangaobo
 */
@org.springframework.stereotype.Component
@lombok.RequiredArgsConstructor
public class IndexTrace {
    /** 应用显式装配的W3C传播器。 */
    private final io.micrometer.tracing.propagation.Propagator propagator;
    /** 当前应用追踪器。 */
    private final io.micrometer.tracing.Tracer tracer;
    /** 安全JSON工厂，不包含正文或服务凭据。 */
    private final com.fasterxml.jackson.databind.ObjectMapper json;
    /** 只保存实际当前span，不以jobId伪造traceId。 */
    public com.fasterxml.jackson.databind.JsonNode capture() {
        var span=tracer.currentSpan();if(span==null)return null;
        var carrier=new java.util.HashMap<String,String>();
        propagator.inject(span.context(),carrier,java.util.Map::put);
        var value=json.createObjectNode().put("schema_version",1);
        for(String name:java.util.List.of("traceparent","tracestate"))
            if(carrier.containsKey(name))value.put(name,carrier.get(name));
        return value.has("traceparent")?value:null;
    }
}
