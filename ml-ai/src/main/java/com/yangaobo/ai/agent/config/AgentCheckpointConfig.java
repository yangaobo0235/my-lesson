package com.yangaobo.ai.agent.config;

import com.alibaba.cloud.ai.graph.checkpoint.BaseCheckpointSaver;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.checkpoint.savers.redis.RedisSaver;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
@EnableConfigurationProperties(AgentCheckpointProperties.class)
public class AgentCheckpointConfig {

    private static final Logger log =
            LoggerFactory.getLogger(AgentCheckpointConfig.class);

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean(RedissonClient.class)
    @ConditionalOnProperty(
            prefix = "ai.agent.checkpoint",
            name = "type",
            havingValue = "redis",
            matchIfMissing = true)
    public RedissonClient agentCheckpointRedissonClient(
            RedisProperties redisProperties) {
        Config config = new Config();
        SingleServerConfig server = config
                .useSingleServer()
                .setAddress(address(redisProperties))
                .setDatabase(redisProperties.getDatabase());
        if (redisProperties.getTimeout() != null) {
            server.setTimeout(
                    (int) redisProperties.getTimeout().toMillis());
        }
        if (StringUtils.hasText(redisProperties.getPassword())) {
            server.setPassword(redisProperties.getPassword());
        }
        if (StringUtils.hasText(redisProperties.getUsername())) {
            server.setUsername(redisProperties.getUsername());
        }
        return Redisson.create(config);
    }

    @Bean
    public BaseCheckpointSaver agentCheckpointSaver(
            AgentCheckpointProperties properties,
            ObjectProvider<RedissonClient> redissonClientProvider) {
        if (properties.getType()
                == AgentCheckpointProperties.CheckpointType.REDIS) {
            RedissonClient redissonClient =
                    redissonClientProvider.getIfAvailable();
            if (redissonClient != null) {
                log.info("Using RedisSaver for MyLesson Agent checkpoint");
                return RedisSaver.builder()
                        .redisson(redissonClient)
                        .build();
            }
            if (!properties.isFallbackToMemory()) {
                throw new IllegalStateException(
                        "Redis checkpoint is enabled but RedissonClient is unavailable");
            }
            log.warn(
                    "Redis checkpoint is enabled but RedissonClient is unavailable, falling back to MemorySaver");
        }
        log.info("Using MemorySaver for MyLesson Agent checkpoint");
        return MemorySaver.builder().build();
    }

    private String address(RedisProperties redisProperties) {
        return "redis://"
                + redisProperties.getHost()
                + ":"
                + redisProperties.getPort();
    }
}
