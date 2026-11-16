package com.yangaobo.servicedesk.knowledge.config;

import com.aliyun.sdk.service.oss2.OSSClient;
import com.aliyun.sdk.service.oss2.credentials.StaticCredentialsProvider;

import java.net.URI;
import java.time.Duration;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 装配knowledge独占的OSS和上传互斥客户端，关闭随应用生命周期执行。
 *
 * @author yangaobo
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "desk.infrastructure.enabled", havingValue = "true")
@EnableConfigurationProperties({OssProperties.class, RedissonProperties.class})
public class StorageConfig {

    /**
     * 配置TLS、禁止重定向及隐式重试，不在启动时上传对象。
     *
     * @param properties 私有桶及访问参数
     * @return 有界OSS客户端
     */
    @Bean(destroyMethod = "close")
    public OSSClient ossClient(OssProperties properties) {
        URI endpoint = URI.create(properties.getEndpoint());
        if (!"https".equals(endpoint.getScheme())
                || endpoint.getHost() == null
                || endpoint.getUserInfo() != null
                || endpoint.getQuery() != null
                || endpoint.getFragment() != null
                || (endpoint.getPath() != null && !endpoint.getPath().isEmpty())) {
            throw new IllegalStateException("desk.oss.endpoint必须是无凭据的HTTPS根地址");
        }
        return OSSClient.newBuilder()
                .region(properties.getRegion())
                .endpoint(properties.getEndpoint())
                .credentialsProvider(
                        new StaticCredentialsProvider(
                                properties.getAccessKeyId(), properties.getAccessKeySecret()))
                .connectTimeout(Duration.ofSeconds(2))
                .readWriteTimeout(Duration.ofSeconds(30))
                .retryMaxAttempts(1)
                .enabledRedirect(false)
                .proxyFromEnvironment(false)
                .build();
    }

    /**
     * 创建懒连接Redisson；watchdog不替代数据库幂等约束。
     *
     * @param properties 上传互斥连接及预算
     * @return 所属应用独立客户端
     */
    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient(RedissonProperties properties) {
        Config config = new Config();
        config.setLazyInitialization(true)
                .setLockWatchdogTimeout(properties.getWatchdogTimeoutMs());
        config.setThreads(2).setNettyThreads(2);
        config.useSingleServer()
                .setAddress(properties.getAddress())
                .setUsername(properties.getUsername())
                .setPassword(properties.getPassword())
                .setDatabase(properties.getDatabase())
                .setConnectionPoolSize(8)
                .setConnectionMinimumIdleSize(1)
                .setSubscriptionConnectionPoolSize(2)
                .setSubscriptionConnectionMinimumIdleSize(1)
                .setConnectTimeout(2000)
                .setTimeout(2000)
                .setRetryAttempts(0);
        return Redisson.create(config);
    }
}
