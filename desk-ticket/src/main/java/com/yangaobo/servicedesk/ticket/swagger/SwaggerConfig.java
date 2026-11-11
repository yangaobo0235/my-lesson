package com.yangaobo.servicedesk.ticket.swagger;

import com.yangaobo.servicedesk.common.env.SystemEnvironment;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * 由所属启动应用显式导入OpenAPI元信息，不通过公共库触发装配。
 *
 * @author yangaobo
 */
public class SwaggerConfig {

    /**
     * 描述当前服务的HTTP合同；安全方案随身份入口实际交付。
     *
     * @param environment 当前应用配置
     * @param systemEnvironment 当前应用唯一环境快照
     * @return OpenAPI3.0元信息
     */
    @Bean
    public OpenAPI serviceOpenApi(Environment environment, SystemEnvironment systemEnvironment) {
        return new OpenAPI().info(new Info()
                .title(environment.getRequiredProperty("spring.application.name"))
                .version("v1")
                .description(systemEnvironment.getBaseEnvironment().getDesc() + "HTTP接口合同"));
    }
}
