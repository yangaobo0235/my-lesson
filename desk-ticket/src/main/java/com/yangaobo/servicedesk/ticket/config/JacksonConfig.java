package com.yangaobo.servicedesk.ticket.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 保留Boot的JSON模块及协议空值，不全局改写数值类型。
 *
 * @author yangaobo
 */
@Configuration(proxyBeanMethods = false)
public class JacksonConfig {

    /**
     * 通过Boot的builder保留data等可空协议字段。
     *
     * @return 仅调整空值包含策略的定制器
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jsonContractCustomizer() {
        return builder -> builder.serializationInclusion(JsonInclude.Include.ALWAYS);
    }
}
