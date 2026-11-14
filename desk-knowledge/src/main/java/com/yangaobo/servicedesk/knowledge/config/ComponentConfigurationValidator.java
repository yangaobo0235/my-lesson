package com.yangaobo.servicedesk.knowledge.config;

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

    /** 当前应用属性源。 */
    private final Environment environment;

    /** 已验证的唯一基础环境。 */
    private final SystemEnvironment systemEnvironment;

    /**
     * 注入应用配置及统一环境快照。
     * @param environment 属性源
     * @param systemEnvironment 基础环境
     */
    public ComponentConfigurationValidator(Environment environment, SystemEnvironment systemEnvironment) {
        this.environment = environment;
        this.systemEnvironment = systemEnvironment;
    }

    /** 校验启用依赖所需参数，错误只包含字段名。 */
    @Override
    public void afterPropertiesSet() {
        for (String key : new String[]{"spring.cloud.nacos.config.password",
                "spring.cloud.nacos.config.namespace",
                "spring.cloud.nacos.discovery.ip",
                "spring.datasource.password",
                "desk.xxl-job.access-token",
                "desk.redisson.password",
                "desk.oss.bucket",
                "desk.oss.access-key-id",
                "desk.oss.access-key-secret",
                "desk.rocketmq.access-key",
                "desk.rocketmq.secret-key"}) {
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
        if (production ? !"prod".equals(resource) : !("test".equals(resource) || "ci".equals(resource))) {
            throw new IllegalStateException("desk.infrastructure.resource-env与基础环境不一致");
        }
        String database = "desk_knowledge_" + resource;
        String jdbc = environment.getProperty("spring.datasource.url", "");
        if (!jdbc.matches("jdbc:mysql://[^/]+/" + database + "\\?.*")) {
            throw new IllegalStateException("spring.datasource.url数据库与环境不一致");
        }
        requireEquals("desk.redisson.key-prefix", "desk:" + resource + ":knowledge:upload:");
        requireEquals("desk.oss.object-prefix", "desk/" + resource + "/originals/");
        requireEquals("desk.rocketmq.topic", "desk-knowledge-index-command-" + resource);
    }

    /**
     * 验证资源名与预期环境完全一致。
     * @param key 属性键
     * @param expected 预期命名
     */
    private void requireEquals(String key, String expected) {
        if (!expected.equals(environment.getProperty(key))) {
            throw new IllegalStateException(key + "与资源环境不一致");
        }
    }
}
