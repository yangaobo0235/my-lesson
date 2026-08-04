package com.yangaobo.ai.outbox;

import com.yangaobo.ai.event.KnowledgeChangeEvent;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.time.Instant;
import java.util.UUID;

@Component
@ConditionalOnProperty(
        name = "ai.knowledge-sync.outbox-enabled",
        havingValue = "true")
public class AiOutboxPublisher {

    private final AiOutboxRepository repository;

    public AiOutboxPublisher(AiOutboxRepository repository) {
        this.repository = repository;
    }

    public void publish(
            String eventType,
            Long aggregateId,
            long version) {
        repository.append(new KnowledgeChangeEvent(
                UUID.randomUUID(),
                eventType,
                aggregateId.toString(),
                version,
                Instant.now()));
    }
}
