package com.yangaobo.servicedesk.user.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 身份持久化装配的业务边界。
 *
 * @author yangaobo
 */
@Configuration
@ConditionalOnProperty(name = "desk.identity.enabled", havingValue = "true", matchIfMissing = true)
@MapperScan("com.yangaobo.servicedesk.user.mapper")
public class IdentityPersistenceConfig {
    /**
     * 使用包含算法标识的密码哈希，不安装默认口令。
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
