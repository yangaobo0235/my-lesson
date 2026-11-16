package com.yangaobo.servicedesk.ticket.swagger;

import com.yangaobo.servicedesk.common.env.SystemEnvironment;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

import java.util.List;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * 由所属启动应用显式导入OpenAPI元信息，不通过公共库触发装配。
 *
 * @author yangaobo
 */
public class SwaggerConfig {

    /**
     * 描述当前服务的HTTP合同；安全方案与真实同源身份入口一致。
     *
     * @param environment       当前应用配置
     * @param systemEnvironment 当前应用唯一环境快照
     * @return OpenAPI3.0元信息
     */
    @Bean
    public OpenAPI serviceOpenApi(Environment environment, SystemEnvironment systemEnvironment) {
        return new OpenAPI()
                .components(
                        new Components()
                                .addSecuritySchemes(
                                        "Session",
                                        new SecurityScheme()
                                                .type(SecurityScheme.Type.APIKEY)
                                                .in(SecurityScheme.In.COOKIE)
                                                .name("DESK_SESSION"))
                                .addSecuritySchemes(
                                        "CSRF",
                                        new SecurityScheme()
                                                .type(SecurityScheme.Type.APIKEY)
                                                .in(SecurityScheme.In.HEADER)
                                                .name("X-CSRF-TOKEN")))
                .info(
                        new Info()
                                .title(environment.getRequiredProperty("spring.application.name"))
                                .version("v1")
                                .description(
                                        systemEnvironment.getBaseEnvironment().getDesc()
                                                + "HTTP接口合同"));
    }

    /**
     * 字段说明只在当前应用显式装配，公共注解不触发Spring扫描。
     */
    @Bean
    public EnumPropertyCustomizer enumPropertyCustomizer() {
        return new EnumPropertyCustomizer();
    }

    /**
     * 写请求将Session与CSRF放在同一要求中，匿名登录仍需CSRF。
     */
    @Bean
    public OpenApiCustomizer browserOpenApiSecurity() {
        return api ->
                api.getPaths()
                        .forEach(
                                (path, item) ->
                                        item.readOperationsMap()
                                                .forEach(
                                                        (method, operation) -> {
                                                            if (!path.startsWith("/api/v1/"))
                                                                return;
                                                            boolean read =
                                                                    method
                                                                            == PathItem.HttpMethod
                                                                            .GET;
                                                            if (path.equals(
                                                                    "/api/v1/users/auth/csrf")) {
                                                                operation.setSecurity(List.of());
                                                            } else {
                                                                var requirement =
                                                                        new SecurityRequirement();
                                                                if (!path.equals(
                                                                        "/api/v1/users/auth/login"))
                                                                    requirement.addList("Session");
                                                                if (!read)
                                                                    requirement.addList("CSRF");
                                                                operation.setSecurity(
                                                                        List.of(requirement));
                                                            }
                                                        }));
    }
}
