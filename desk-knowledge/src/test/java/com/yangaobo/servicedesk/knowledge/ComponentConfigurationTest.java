package com.yangaobo.servicedesk.knowledge;

import com.yangaobo.servicedesk.knowledge.config.ComponentConfigurationValidator;
import com.yangaobo.servicedesk.knowledge.config.EnvironmentConfig;
import com.yangaobo.servicedesk.knowledge.config.OssProperties;
import com.yangaobo.servicedesk.knowledge.config.RocketMqConfig;
import org.apache.rocketmq.client.apis.producer.Producer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证资源环境、凭据安全和无真实回查器时禁止启动生产者。
 *
 * @author yangaobo
 */
class ComponentConfigurationTest {

    /** 合法配置通过，空凭据或跨环境数据库拒绝，不输出密码。 */
    @Test
    void requiresCredentialsAndOwnedResourceNames() {
        runner().run(context -> assertThat(context).hasNotFailed());
        runner().withPropertyValues("desk.oss.access-key-secret=").run(context ->
                assertThat(context).hasFailed());
        runner().withPropertyValues("spring.datasource.url=jdbc:mysql://host/desk_knowledge_prod?password=private-value")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasStackTraceContaining("数据库与环境不一致");
                    assertThat(context.getStartupFailure().getMessage()).doesNotContain("private-value");
                });
    }

    /** 骨架不注册假回查器，事务生产者只在业务回查器存在时建立。 */
    @Test
    void doesNotCreateProducerWithoutRealTransactionChecker() {
        new ApplicationContextRunner().withUserConfiguration(RocketMqConfig.class)
                .withPropertyValues("desk.infrastructure.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(Producer.class);
                });
        OssProperties properties = new OssProperties();
        properties.setAccessKeyId("private-id");
        properties.setAccessKeySecret("private-value");
        assertThat(properties.toString()).doesNotContain("private-id", "private-value");
    }

    /**
     * 构造只含配置校验的上下文，不启用外部SDK。
     * @return 隔离运行器
     */
    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner()
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("dev"))
                .withUserConfiguration(EnvironmentConfig.class, ComponentConfigurationValidator.class)
                .withPropertyValues("desk.infrastructure.enabled=true", "desk.infrastructure.resource-env=test",
                        "spring.cloud.nacos.config.password=fake", "spring.cloud.nacos.config.namespace=desk-test",
                        "spring.cloud.nacos.discovery.ip=192.168.23.100", "spring.datasource.password=fake",
                        "spring.datasource.url=jdbc:mysql://host:3306/desk_knowledge_test?connectionTimeZone=UTC",
                        "desk.xxl-job.access-token=fake", "desk.redisson.password=fake", "desk.oss.bucket=fake",
                        "desk.oss.access-key-id=fake", "desk.oss.access-key-secret=fake",
                        "desk.rocketmq.access-key=fake", "desk.rocketmq.secret-key=fake",
                        "desk.redisson.key-prefix=desk:test:knowledge:upload:",
                        "desk.oss.object-prefix=desk/test/originals/",
                        "desk.rocketmq.topic=desk-knowledge-index-command-test");
    }
}
