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
@ConfigurationProperties("desk.redisson")
public class RedissonProperties implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Redis私网地址。 */
    private String address;

    /** ACL用户。 */
    private String username;

    /** ACL密码。 */
    @ToString.Exclude
    private String password;

    /** 逻辑库。 */
    private int database;

    /** watchdog续约毫秒。 */
    private long watchdogTimeoutMs;

    /** 业务等锁上限毫秒。 */
    private long waitTimeoutMs;

    /** 上传锁前缀。 */
    private String keyPrefix;
}
