package com.yangaobo.ai.sync.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangaobo.ai.sync.model.KnowledgeChangeEvent;
import com.yangaobo.ai.sync.service.IncrementalKnowledgeSyncService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Consumer;

@Configuration
public class KnowledgeSyncConsumerConfig {

    @Bean
    public Consumer<String> knowledgeSyncConsumer(
            ObjectMapper objectMapper,
            IncrementalKnowledgeSyncService syncService) {
        return payload -> {
            try {
                syncService.handle(
                        objectMapper.readValue(payload, KnowledgeChangeEvent.class));
            } catch (RuntimeException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new IllegalArgumentException(
                        "Invalid knowledge event payload", exception);
            }
        };
    }
}
