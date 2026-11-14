package com.yangaobo.servicedesk.knowledge.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangaobo.servicedesk.knowledge.config.RocketMqProperties;
import com.yangaobo.servicedesk.knowledge.model.dto.IndexCommand;
import org.apache.rocketmq.client.apis.ClientServiceProvider;
import org.apache.rocketmq.client.apis.producer.Producer;
import org.apache.rocketmq.client.apis.producer.Transaction;

/**
 * 只发送原生事务半消息，提交决策由已持久本地业务事实提供。
 *
 * @author yangaobo
 */
@org.springframework.stereotype.Component
@lombok.RequiredArgsConstructor
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "desk.knowledge.enabled", havingValue = "true", matchIfMissing = true)
public class IndexCommandProducer {
    private final org.springframework.beans.factory.ObjectProvider<Producer> producers;
    private final RocketMqProperties properties;
    private final ObjectMapper json;
    /** 原任务追踪独立于业务信封。 */
    private final com.yangaobo.servicedesk.knowledge.mapper.IndexJobMapper jobs;
    /** 只有明确返回半消息接收回执后，调用方才允许确认本地事务。 */
    public Transaction prepare(IndexCommand command) throws Exception {
        byte[] bytes=json.writeValueAsBytes(command);
        if(bytes.length>65536)throw new IllegalArgumentException("索引信封超过预算");
        Producer producer=producers.getObject();Transaction transaction=producer.beginTransaction();
        var builder=ClientServiceProvider.loadService().newMessageBuilder().setTopic(properties.getTopic())
            .setKeys(command.getJobId()).setBody(bytes);
        var job=jobs.selectById(command.getJobId());
        if(job!=null && job.getTraceContext()!=null) {
            for(String name:java.util.List.of("traceparent","tracestate")) {
                var value=job.getTraceContext().get(name);
                if(value!=null && value.isTextual())builder.addProperty(name,value.asText());
            }
        }
        producer.send(builder.build(),transaction);return transaction;
    }
}
