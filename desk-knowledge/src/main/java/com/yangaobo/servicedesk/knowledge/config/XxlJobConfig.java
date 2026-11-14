package com.yangaobo.servicedesk.knowledge.config;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 显式装配执行器；真实任务处理器由所属业务用例注册。
 *
 * @author yangaobo
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "desk.infrastructure.enabled", havingValue = "true")
@EnableConfigurationProperties(XxlJobProperties.class)
public class XxlJobConfig {

    /**
     * 创建由Spring启动和关闭的执行器，不注册空成功处理器。
     *
     * @param properties 已绑定的执行器参数
     * @return 独立服务执行器
     */
    @Bean
    public XxlJobSpringExecutor xxlJobExecutor(XxlJobProperties properties) {
        if (properties.getAccessToken() == null || properties.getAccessToken().isBlank()) {
            throw new IllegalStateException("desk.xxl-job.access-token不能为空");
        }
        XxlJobSpringExecutor executor = new XxlJobSpringExecutor();
        executor.setAdminAddresses(properties.getAdminAddresses());
        executor.setAccessToken(properties.getAccessToken());
        if(properties.getTimeoutSeconds()<1 || properties.getTimeoutSeconds()>10)
            throw new IllegalStateException("desk.xxl-job.timeout-seconds必须为1至10秒");
        executor.setTimeout(properties.getTimeoutSeconds());
        executor.setAppname(properties.getAppName());
        executor.setAddress(properties.getAddress());
        executor.setIp(properties.getIp());
        executor.setPort(properties.getPort());
        executor.setLogPath(properties.getLogPath());
        executor.setLogRetentionDays(properties.getLogRetentionDays());
        return executor;
    }
}
