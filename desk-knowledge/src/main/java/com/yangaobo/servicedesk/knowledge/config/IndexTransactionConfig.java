package com.yangaobo.servicedesk.knowledge.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangaobo.servicedesk.knowledge.model.dto.IndexCommand;
import com.yangaobo.servicedesk.knowledge.service.IIndexJobService;
import org.apache.rocketmq.client.apis.producer.TransactionChecker;
import org.apache.rocketmq.client.apis.producer.TransactionResolution;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 注册真实业务事实回查器，生产者装配不再使用空占位。
 *
 * @author yangaobo
 */
@Configuration
@ConditionalOnProperty(name = "desk.knowledge.enabled", havingValue = "true", matchIfMissing = true)
public class IndexTransactionConfig {
    /**
     * 延迟取得业务用例，避免生产者与回查器形成构造循环。
     */
    @Bean
    public TransactionChecker indexTransactionChecker(
            ObjectProvider<IIndexJobService> services, ObjectMapper json) {
        return message -> {
            try {
                var buffer = message.getBody();
                if (buffer.remaining() > 65536) return TransactionResolution.UNKNOWN;
                byte[] bytes = new byte[buffer.remaining()];
                buffer.get(bytes);
                var command = json.readValue(bytes, IndexCommand.class);
                return services.getObject().check(command);
            } catch (Exception unavailable) {
                return TransactionResolution.UNKNOWN;
            }
        };
    }
}
