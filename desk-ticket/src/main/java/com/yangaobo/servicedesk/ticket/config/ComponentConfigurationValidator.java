package com.yangaobo.servicedesk.ticket.config;

import com.yangaobo.servicedesk.common.env.SystemEnvironment;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * 应用启动前核对必需凭据和资源环境，不打印连接值或访问业务数据。
 *
 * @author yangaobo
 */
@Component
@ConditionalOnProperty(name = "desk.infrastructure.enabled", havingValue = "true")
public class ComponentConfigurationValidator implements InitializingBean {

    /**
     * 当前应用属性源。
     */
    private final Environment environment;

    /**
     * 已验证的唯一基础环境。
     */
    private final SystemEnvironment systemEnvironment;

    /**
     * 注入应用配置及统一环境快照。
     *
     * @param environment       属性源
     * @param systemEnvironment 基础环境
     */
    public ComponentConfigurationValidator(
            Environment environment, SystemEnvironment systemEnvironment) {
        this.environment = environment;
        this.systemEnvironment = systemEnvironment;
    }

    /**
     * 校验启用依赖所需参数，错误只包含字段名。
     */
    @Override
    public void afterPropertiesSet() {
        for (String key :
                new String[]{
                        "spring.cloud.nacos.config.password",
                        "spring.cloud.nacos.config.namespace",
                        "spring.cloud.nacos.discovery.ip",
                        "spring.datasource.password",
                        "desk.xxl-job.access-token"
                }) {
            String value;
            try {
                value = environment.getProperty(key);
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException(key + "缺失或无效");
            }
            if (value == null || value.isBlank()) {
                throw new IllegalStateException(key + "不能为空");
            }
        }
        String resource = environment.getProperty("desk.infrastructure.resource-env", "");
        boolean production = "prod".equals(systemEnvironment.getBaseEnvironment().getValue());
        if (production
                ? !"prod".equals(resource)
                : !("test".equals(resource) || "ci".equals(resource))) {
            throw new IllegalStateException("desk.infrastructure.resource-env与基础环境不一致");
        }
        String database = "desk_ticket_" + resource;
        String jdbc = environment.getProperty("spring.datasource.url", "");
        if (!jdbc.matches("jdbc:mysql://[^/]+/" + database + "\\?.*")) {
            throw new IllegalStateException("spring.datasource.url数据库与环境不一致");
        }
    }
}
