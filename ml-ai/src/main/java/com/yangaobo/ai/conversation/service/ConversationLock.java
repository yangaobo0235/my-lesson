package com.yangaobo.ai.conversation.service;

import com.yangaobo.ai.conversation.config.ConversationProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class ConversationLock {

    private static final String KEY_PREFIX = "ai:conversation:lock:";
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
    private final ConversationProperties properties;

    public ConversationLock(
            StringRedisTemplate redisTemplate,
            ConversationProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    public Optional<LockLease> tryAcquire(UUID conversationId) {
        String key = KEY_PREFIX + conversationId;
        String token = UUID.randomUUID().toString();
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(key, token, properties.getLockTtl());
        return Boolean.TRUE.equals(acquired)
                ? Optional.of(new LockLease(key, token))
                : Optional.empty();
    }

    public void release(LockLease lease) {
        redisTemplate.execute(
                RELEASE_SCRIPT,
                List.of(lease.key()),
                lease.token());
    }

    public record LockLease(
            String key,
            String token
    ) {
    }
}
