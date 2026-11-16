package com.yangaobo.servicedesk.gateway.config;

import com.yangaobo.servicedesk.common.env.SystemEnvironment;

import java.net.URI;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * 在启动时校验实际使用的下游配置，不连接或探测真实业务服务。
 *
 * @author yangaobo
 */
@Component
public class InfrastructureConfigurationValidator implements InitializingBean {

    /**
     * 当前应用唯一基础环境快照。
     */
    private final SystemEnvironment systemEnvironment;

    /**
     * 所属应用实际生效配置。
     */
    private final Environment environment;

    /**
     * 注入统一环境及所属配置，不重复解析Profile。
     *
     * @param systemEnvironment 已验证的基础环境
     * @param environment       当前属性来源
     */
    public InfrastructureConfigurationValidator(
            SystemEnvironment systemEnvironment, Environment environment) {
        this.systemEnvironment = systemEnvironment;
        this.environment = environment;
    }

    /**
     * 校验四个真实路由目标；错误仅报告配置名，不输出凭据或完整地址。
     */
    @Override
    public void afterPropertiesSet() {
        for (String service : new String[]{"user", "ticket", "knowledge", "ai"}) {
            String key = "desk.downstream." + service;
            URI target;
            try {
                target = URI.create(environment.getRequiredProperty(key));
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException(errorMessage(key));
            }
            boolean discoveredJavaService =
                    !"ai".equals(service) && ("lb://desk-" + service).equals(target.toString());
            if (!discoveredJavaService
                    && (!("http".equals(target.getScheme()) || "https".equals(target.getScheme()))
                    || target.getHost() == null
                    || target.getUserInfo() != null
                    || target.getQuery() != null
                    || target.getFragment() != null
                    || (target.getPath() != null
                    && !target.getPath().isEmpty()
                    && !"/".equals(target.getPath()))
                    || target.getPort() == 0
                    || target.getPort() > 65535)) {
                throw new IllegalStateException(errorMessage(key));
            }
        }
    }

    /**
     * 提供不包含敏感连接内容的配置定位信息。
     *
     * @param key 配置键
     * @return 环境及字段说明
     */
    private String errorMessage(String key) {
        return systemEnvironment.getBaseEnvironment().getValue()
                + "环境的"
                + key
                + "必须是无凭据、查询串及路径前缀的HTTP(S)服务地址或所属Java服务的精确lb地址";
    }
}
