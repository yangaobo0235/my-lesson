package com.yangaobo.servicedesk.user.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 在执行结构迁移前验证数据库归属和必需配置，失败时中止应用启动。
 *
 * @author yangaobo
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "spring.flyway.enabled", havingValue = "true", matchIfMissing = true)
public class DatabaseMigrationConfig {

    /**
     * 在DDL边界复用配置验证，避免错误的数据库地址先产生迁移副作用。
     *
     * @param configurationValidator 本应用的配置验证器
     * @return 验证后执行原生Flyway迁移的启动策略
     */
    @Bean
    public FlywayMigrationStrategy validatedDatabaseMigration(
            ComponentConfigurationValidator configurationValidator) {
        return flyway -> {
            configurationValidator.afterPropertiesSet();
            flyway.migrate();
        };
    }
}
