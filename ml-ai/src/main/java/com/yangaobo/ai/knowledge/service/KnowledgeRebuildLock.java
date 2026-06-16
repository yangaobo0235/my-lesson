package com.yangaobo.ai.knowledge.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class KnowledgeRebuildLock {

    private static final String LOCK_KEY = "ai:knowledge:rebuild:lock";
    private static final Duration LOCK_TTL = Duration.ofHours(2);
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT =
            new DefaultRedisScript<>(
                    """
                    if redis.call('get', KEYS[1]) == ARGV[1] then
                        return redis.call('del', KEYS[1])
                    end
                    return 0
                    """,
                    Long.class);

    private final StringRedisTemplate redisTemplate;

    public KnowledgeRebuildLock(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public Optional<String> tryAcquire() {
        String token = UUID.randomUUID().toString();
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(LOCK_KEY, token, LOCK_TTL);
        return Boolean.TRUE.equals(acquired)
                ? Optional.of(token)
                : Optional.empty();
    }

    public boolean isLocked() {
        return Boolean.TRUE.equals(redisTemplate.hasKey(LOCK_KEY));
    }

    public void release(String token) {
        redisTemplate.execute(RELEASE_SCRIPT, List.of(LOCK_KEY), token);
    }
}
