package com.yangaobo.ai.conversation.service;

import com.yangaobo.ai.conversation.config.ConversationProperties;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConversationLockTest {

    @Test
    void shouldAcquireLeaseWithConfiguredTtl() {
        StringRedisTemplate redisTemplate =
                mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations =
                mock(ValueOperations.class);
        ConversationProperties properties = new ConversationProperties();
        properties.setLockTtl(Duration.ofSeconds(45));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(
                anyString(),
                anyString(),
                eq(Duration.ofSeconds(45)))).thenReturn(true);

        Optional<ConversationLock.LockLease> lease =
                new ConversationLock(redisTemplate, properties)
                        .tryAcquire(UUID.randomUUID());

        assertThat(lease).isPresent();
        assertThat(lease.orElseThrow().key())
                .startsWith("ai:conversation:lock:");
    }
}
