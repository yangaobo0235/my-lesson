package com.yangaobo.servicedesk.ticket.config;

import com.aliyun.sdk.service.oss2.OSSClient;
import com.aliyun.sdk.service.oss2.credentials.StaticCredentialsProvider;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * 附件客户端禁止覆盖、重定向及隐式重试，生命周期随 ticket。
 *
 * @author yangaobo
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AttachmentStorageProperties.class)
@ConditionalOnProperty(name = "desk.ticket.enabled", havingValue = "true", matchIfMissing = true)
public class AttachmentStorageConfig {
    /**
     * 校验环境和 TLS 端点后构造有界客户端，不在启动时外写。
     */
    @Bean(destroyMethod = "close")
    public OSSClient attachmentOssClient(AttachmentStorageProperties properties, Environment environment) {
        URI endpoint = URI.create(properties.getEndpoint());
        if (!"https".equals(endpoint.getScheme()) || endpoint.getHost() == null
                || endpoint.getUserInfo() != null || endpoint.getQuery() != null || endpoint.getFragment() != null
                || (endpoint.getPath() != null && !endpoint.getPath().isEmpty()))
            throw new IllegalStateException("desk.attachment-storage.endpoint 必须是 HTTPS 根地址");
        String resource = environment.getRequiredProperty("desk.infrastructure.resource-env");
        if (!properties.getObjectPrefix().equals("ticket/" + resource + "/attachments/"))
            throw new IllegalStateException("附件前缀必须属于当前 ticket 环境");
        return OSSClient.newBuilder().region(properties.getRegion()).endpoint(properties.getEndpoint())
                .credentialsProvider(new StaticCredentialsProvider(properties.getAccessKeyId(), properties.getAccessKeySecret()))
                .connectTimeout(Duration.ofSeconds(2)).readWriteTimeout(Duration.ofSeconds(10))
                .retryMaxAttempts(1).enabledRedirect(false).proxyFromEnvironment(false).build();
    }
}
