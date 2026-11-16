package com.yangaobo.servicedesk.user.config;

import com.yangaobo.servicedesk.user.service.IBootstrapService;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 受控首名管理员初始化的业务边界。
 *
 * @author yangaobo
 */
@Configuration
@EnableConfigurationProperties(BootstrapProperties.class)
@ConditionalOnProperty(name = "desk.bootstrap.enabled", havingValue = "true")
public class BootstrapConfig {
    /**
     * 只在显式开启时执行；没有默认密码和HTTP初始化入口。
     */
    @Bean
    public ApplicationRunner initializeIdentity(
            IBootstrapService bootstrapService, BootstrapProperties properties) {
        return arguments -> bootstrapService.initialize(properties);
    }
}
