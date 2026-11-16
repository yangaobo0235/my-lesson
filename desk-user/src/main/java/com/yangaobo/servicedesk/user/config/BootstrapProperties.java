package com.yangaobo.servicedesk.user.config;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 受控首名管理员初始化的业务边界。
 *
 * @author yangaobo
 */
@Data
@NoArgsConstructor
@ConfigurationProperties("desk.bootstrap")
public class BootstrapProperties implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 仅在受控初始化时启用。
     */
    private boolean enabled;

    /**
     * 唯一服务商代码。
     */
    private String organizationCode;

    /**
     * 服务商名称。
     */
    private String organizationName;

    /**
     * 首名管理员账号。
     */
    private String username;

    /**
     * 初始口令，不设默认值。
     */
    @ToString.Exclude
    private String password;
}
