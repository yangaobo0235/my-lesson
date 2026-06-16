package com.yangaobo.ai.knowledge.service;

import com.yangaobo.ai.knowledge.model.KnowledgeIndexStatus;
import com.yangaobo.ai.knowledge.repository.KnowledgeSourceRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Component
public class KnowledgeIndexStatusStore {

    private static final String STATUS_KEY = "ai:knowledge:rebuild:status";

    private final StringRedisTemplate redisTemplate;
    private final KnowledgeRebuildLock rebuildLock;
    private final KnowledgeSourceRepository sourceRepository;

    public KnowledgeIndexStatusStore(
            StringRedisTemplate redisTemplate,
            KnowledgeRebuildLock rebuildLock,
            KnowledgeSourceRepository sourceRepository) {
        this.redisTemplate = redisTemplate;
        this.rebuildLock = rebuildLock;
        this.sourceRepository = sourceRepository;
    }

    public void running() {
        Map<String, String> values = new HashMap<>();
        values.put("state", "RUNNING");
        values.put("startedAt", Instant.now().toString());
        values.put("finishedAt", "");
        values.put("indexedSources", "0");
        values.put("skippedSources", "0");
        values.put("failedSources", "0");
        values.put("indexedChunks", "0");
        values.put("message", "Knowledge rebuild is running");
        redisTemplate.opsForHash().putAll(STATUS_KEY, values);
    }

    public void finished(RebuildCounters counters) {
        writeCompleted(
                counters.failedSources() == 0 ? "COMPLETED" : "COMPLETED_WITH_ERRORS",
                counters,
                counters.failedSources() == 0
                        ? "Knowledge rebuild completed"
                        : "Knowledge rebuild completed with errors");
    }

    public void failed(RebuildCounters counters, String message) {
        writeCompleted("FAILED", counters, message);
    }

    public KnowledgeIndexStatus get() {
        Map<Object, Object> values = redisTemplate.opsForHash().entries(STATUS_KEY);
        return new KnowledgeIndexStatus(
                string(values, "state", "IDLE"),
                rebuildLock.isLocked(),
                instant(values, "startedAt"),
                instant(values, "finishedAt"),
                number(values, "indexedSources"),
                number(values, "skippedSources"),
                number(values, "failedSources"),
                number(values, "indexedChunks"),
                sourceRepository.countActiveSourcesByType(),
                sourceRepository.countVectorsByType(),
                string(values, "message", "No knowledge rebuild has run"));
    }

    private void writeCompleted(
            String state,
            RebuildCounters counters,
            String message) {
        Map<String, String> values = new HashMap<>();
        values.put("state", state);
        values.put("finishedAt", Instant.now().toString());
        values.put("indexedSources", Long.toString(counters.indexedSources()));
        values.put("skippedSources", Long.toString(counters.skippedSources()));
        values.put("failedSources", Long.toString(counters.failedSources()));
        values.put("indexedChunks", Long.toString(counters.indexedChunks()));
        values.put("message", message);
        redisTemplate.opsForHash().putAll(STATUS_KEY, values);
    }

    private String string(
            Map<Object, Object> values,
            String key,
            String defaultValue) {
        Object value = values.get(key);
        return value == null || value.toString().isBlank()
                ? defaultValue
                : value.toString();
    }

    private long number(Map<Object, Object> values, String key) {
        try {
            return Long.parseLong(string(values, key, "0"));
        } catch (NumberFormatException exception) {
            return 0L;
        }
    }

    private Instant instant(Map<Object, Object> values, String key) {
        String value = string(values, key, "");
        return value.isBlank() ? null : Instant.parse(value);
    }

    public record RebuildCounters(
            long indexedSources,
            long skippedSources,
            long failedSources,
            long indexedChunks
    ) {
    }
}
