package com.yangaobo.servicedesk.user.config;

import com.yangaobo.servicedesk.common.env.SystemEnvironment;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * 在所属应用中一次解析基础环境，避免配置组件重复判断Profile。
 *
 * @author yangaobo
 */
@Configuration(proxyBeanMethods = false)
public class EnvironmentConfig {

    /**
     * 从实际生效的Profile建立无外部连接的环境快照。
     *
     * @param environment 当前应用环境
     * @return 唯一基础环境
     */
    @Bean
    public SystemEnvironment systemEnvironment(Environment environment) {
        String[] profiles = environment.getActiveProfiles();
        if (profiles.length == 0) {
            profiles = environment.getDefaultProfiles();
        }
        return SystemEnvironment.fromProfiles(profiles);
    }
}
