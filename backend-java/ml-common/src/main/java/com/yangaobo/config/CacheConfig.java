package com.yangaobo.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.Resource;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;

/**
 * @author 杨奥博
 */
@EnableCaching
@Configuration
public class CacheConfig {
    /** 缓存有效期，单位分钟 */
    private static final long TTL = 30L;
    @Resource
    private RedisConnectionFactory connectionFactory;

    @Bean
    public CacheManager cacheManager() {
        // Jackson核心对象
        ObjectMapper objectMapper = new ObjectMapper();
        /*
         * 设置Jackson在序列化和反序列化过程中，对Java泛型的默认处理方案
         *
         * param1: 一个用于验证泛型类型的对象
         * param2: 表示在序列化和反序列化过程中，将Java泛型类型信息作为属性进行处理
         * param3: 表示在JSON数据中，将Java泛型类型信息作为属性进行存储
         */
        objectMapper.activateDefaultTyping(
                objectMapper.getPolymorphicTypeValidator(),
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY);
        // 设置LocalDateTime序列化方式
        objectMapper.registerModule(new JavaTimeModule());
        // 设置objectMapper的访问权限: Jackson库可以访问Java对象的任何属性
        objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        // Jackson序列化器: 用于相互转换Java对象和JSON字符串，默认使用JDK的序列化方式（中文有乱码）
        Jackson2JsonRedisSerializer<Object> serializer = new Jackson2JsonRedisSerializer<>(objectMapper, Object.class);
        // 配置redis缓存管理对象: 设置缓存过期时间，value值的序列化方式和对Null值的处理方案
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(TTL))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer))
                .disableCachingNullValues();
        // 创建并返回Cache管理器
        return RedisCacheManager.builder(connectionFactory).cacheDefaults(config).build();
    }
}
