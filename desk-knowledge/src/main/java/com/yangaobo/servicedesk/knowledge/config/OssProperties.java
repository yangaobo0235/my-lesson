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
@ConfigurationProperties("desk.oss")
public class OssProperties implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 区域。
     */
    private String region;

    /**
     * HTTPS端点。
     */
    private String endpoint;

    /**
     * 私有原件桶。
     */
    private String bucket;

    /**
     * 访问标识。
     */
    @ToString.Exclude
    private String accessKeyId;

    /**
     * 访问密钥。
     */
    @ToString.Exclude
    private String accessKeySecret;

    /**
     * 环境原件前缀。
     */
    private String objectPrefix;
}
