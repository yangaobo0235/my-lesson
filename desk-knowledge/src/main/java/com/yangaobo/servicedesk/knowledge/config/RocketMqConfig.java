package com.yangaobo.servicedesk.knowledge.config;

import java.time.Duration;
import org.apache.rocketmq.client.apis.ClientConfiguration;
import org.apache.rocketmq.client.apis.ClientServiceProvider;
import org.apache.rocketmq.client.apis.SessionCredentialsProvider;
import org.apache.rocketmq.client.apis.StaticSessionCredentialsProvider;
import org.apache.rocketmq.client.apis.producer.Producer;
import org.apache.rocketmq.client.apis.producer.TransactionChecker;
import org.apache.rocketmq.client.apis.ClientException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 原生事务生产者装配；只有真实业务事实回查器注册后才连接Broker。
 *
 * @author yangaobo
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "desk.infrastructure.enabled", havingValue = "true")
@EnableConfigurationProperties(RocketMqProperties.class)
public class RocketMqConfig {

    /**
     * 启用有回查能力的事务生产者，不使用空回查器或outbox旁路。
     * @param properties 所属Topic、ACL和超时
     * @param checker 读取本地业务事实的回查器
     * @return 关闭时释放的生产者
     * @throws ClientException Proxy连接或协议初始化失败
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnBean(TransactionChecker.class)
    public Producer rocketMqProducer(RocketMqProperties properties, TransactionChecker checker)
            throws ClientException {
        SessionCredentialsProvider credentials = new StaticSessionCredentialsProvider(
                properties.getAccessKey(), properties.getSecretKey());
        ClientConfiguration client = ClientConfiguration.newBuilder().setEndpoints(properties.getEndpoints())
                .setCredentialProvider(credentials).enableSsl(properties.isTlsEnabled())
                .setRequestTimeout(Duration.ofSeconds(properties.getRequestTimeoutSeconds()))
                .setMaxStartupAttempts(2).build();
        return ClientServiceProvider.loadService().newProducerBuilder().setClientConfiguration(client)
                .setTopics(properties.getTopic()).setMaxAttempts(1).setTransactionChecker(checker).build();
    }
}
