package com.yangaobo.ai.outbox;

import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

@Component
@ConditionalOnProperty(
        name = "ai.knowledge-sync.outbox-enabled",
        havingValue = "true")
public class AiOutboxRelay {

    private static final Logger log =
            LoggerFactory.getLogger(AiOutboxRelay.class);

    private final AiOutboxRepository repository;
    private final RocketMQTemplate rocketMQTemplate;
    private final String topic;
    private final int batchSize;

    public AiOutboxRelay(
            AiOutboxRepository repository,
            RocketMQTemplate rocketMQTemplate,
            MeterRegistry meterRegistry,
            @Value("${ai.knowledge-sync.topic:ml-ai-knowledge-events}")
            String topic,
            @Value("${ai.knowledge-sync.outbox-batch-size:100}")
            int batchSize) {
        this.repository = repository;
        this.rocketMQTemplate = rocketMQTemplate;
        this.topic = topic;
        this.batchSize = batchSize;
        Gauge.builder(
                        "outbox_pending_total",
                        repository,
                        value -> value.pendingCount())
                .register(meterRegistry);
        Gauge.builder(
                        "outbox_oldest_age",
                        repository,
                        value -> value.oldestAgeSeconds())
                .baseUnit("seconds")
                .register(meterRegistry);
    }

    @Scheduled(
            fixedDelayString =
                    "${ai.knowledge-sync.outbox-poll-interval:3000}")
    public void relay() {
        for (AiOutboxRepository.OutboxRow row
                : repository.findReady(batchSize)) {
            try {
                rocketMQTemplate.syncSend(topic, row.payload());
                repository.markSent(row.eventId());
            } catch (RuntimeException exception) {
                repository.markFailed(
                        row.eventId(),
                        row.retryCount() + 1,
                        exception.getClass().getSimpleName());
                log.warn(
                        "Knowledge outbox event {} send failed",
                        row.eventId());
            }
        }
    }
}
