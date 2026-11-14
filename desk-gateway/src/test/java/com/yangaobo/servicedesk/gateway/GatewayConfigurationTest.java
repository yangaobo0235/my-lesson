package com.yangaobo.servicedesk.gateway;

import com.yangaobo.servicedesk.gateway.config.EnvironmentConfig;
import com.yangaobo.servicedesk.gateway.config.InfrastructureConfigurationValidator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证启动配置失败关闭，拒绝含凭据或路径前缀的下游地址。
 *
 * @author yangaobo
 */
class GatewayConfigurationTest {

    /**
     * 接受明确的HTTP服务根地址，不发起网络连接。
     */
    @Test
    void acceptsExplicitServiceOrigins() {
        runner("https://knowledge.internal:26104").run(context -> assertThat(context).hasNotFailed());
        runner("lb://desk-knowledge").run(context -> assertThat(context).hasNotFailed());
    }

    /**
     * 无效配置拒绝启动；错误不包含地址中的敏感值。
     */
    @Test
    void rejectsCredentialAndPathBearingOrigins() {
        for (String target : new String[]{"http://user:secret-value@host", "http://host/prefix", "file:///tmp",
                "lb://desk-user", "lb://desk-knowledge/private"}) {
            runner(target).run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).hasStackTraceContaining("desk.downstream.knowledge");
                assertThat(context.getStartupFailure()).hasStackTraceContaining("dev环境");
                assertThat(context.getStartupFailure()).hasStackTraceContaining("HTTP(S)");
                assertThat(context.getStartupFailure().toString()).doesNotContain("secret-value");
            });
        }
    }

    /**
     * 构造只含配置Bean的隔离上下文，不启动网关或任何基础设施。
     *
     * @param knowledge 资料服务目标
     * @return 待执行上下文
     */
    private ApplicationContextRunner runner(String knowledge) {
        return new ApplicationContextRunner()
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("dev"))
                .withUserConfiguration(EnvironmentConfig.class, InfrastructureConfigurationValidator.class)
                .withPropertyValues("desk.downstream.user=http://user:26102",
                        "desk.downstream.ticket=http://ticket:26103",
                        "desk.downstream.knowledge=" + knowledge,
                        "desk.downstream.ai=http://ai:26105");
    }
}
