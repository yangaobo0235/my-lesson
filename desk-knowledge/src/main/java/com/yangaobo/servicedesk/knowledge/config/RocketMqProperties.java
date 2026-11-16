package com.yangaobo.servicedesk.knowledge.config;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * knowledge所属组件参数，不向其他应用传播凭据。
 *
 * @author yangaobo
 */
@Data
@ConfigurationProperties("desk.rocketmq")
public class RocketMqProperties implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * gRPC Proxy地址。
     */
    private String endpoints;

    /**
     * 生产者访问标识。
     */
    @ToString.Exclude
    private String accessKey;

    /**
     * 生产者访问密钥。
     */
    @ToString.Exclude
    private String secretKey;

    /**
     * Proxy是否启用TLS。
     */
    private boolean tlsEnabled;

    /**
     * 事务指令Topic。
     */
    private String topic;

    /**
     * 请求秒数。
     */
    private int requestTimeoutSeconds;
}
