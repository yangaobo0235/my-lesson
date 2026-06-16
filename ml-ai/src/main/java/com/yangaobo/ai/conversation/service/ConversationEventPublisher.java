package com.yangaobo.ai.conversation.service;

import com.yangaobo.ai.conversation.config.ConversationProperties;
import com.yangaobo.ai.conversation.model.ConversationEventType;
import com.yangaobo.ai.conversation.model.ConversationStreamEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class ConversationEventPublisher {

    private final Map<StreamKey, CopyOnWriteArrayList<SseEmitter>> emitters =
            new ConcurrentHashMap<>();
    private final ConversationProperties properties;

    public ConversationEventPublisher(ConversationProperties properties) {
        this.properties = properties;
    }

    public SseEmitter open(Long userId, UUID conversationId) {
        StreamKey key = new StreamKey(userId, conversationId);
        SseEmitter emitter =
                new SseEmitter(properties.getStreamTimeout().toMillis());
        emitters.computeIfAbsent(key, ignored -> new CopyOnWriteArrayList<>())
                .add(emitter);
        emitter.onCompletion(() -> remove(key, emitter));
        emitter.onTimeout(() -> remove(key, emitter));
        emitter.onError(error -> remove(key, emitter));
        try {
            emitter.send(SseEmitter.event()
                    .comment("connected")
                    .reconnectTime(3000L));
        } catch (IOException exception) {
            remove(key, emitter);
            emitter.completeWithError(exception);
        }
        return emitter;
    }

    public void publish(
            Long userId,
            UUID conversationId,
            ConversationEventType type,
            UUID runId,
            UUID requestId,
            String traceId,
            Map<String, Object> data) {
        StreamKey key = new StreamKey(userId, conversationId);
        List<SseEmitter> active = emitters.get(key);
        if (active == null || active.isEmpty()) {
            return;
        }
        ConversationStreamEvent event = new ConversationStreamEvent(
                type.eventName(),
                conversationId,
                runId,
                requestId,
                traceId,
                Instant.now(),
                Map.copyOf(data));
        for (SseEmitter emitter : active) {
            try {
                emitter.send(SseEmitter.event()
                        .name(type.eventName())
                        .data(event));
            } catch (IOException | IllegalStateException exception) {
                remove(key, emitter);
                emitter.complete();
            }
        }
    }

    private void remove(StreamKey key, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> active = emitters.get(key);
        if (active == null) {
            return;
        }
        active.remove(emitter);
        if (active.isEmpty()) {
            emitters.remove(key, active);
        }
    }

    private record StreamKey(
            Long userId,
            UUID conversationId
    ) {
    }
}
