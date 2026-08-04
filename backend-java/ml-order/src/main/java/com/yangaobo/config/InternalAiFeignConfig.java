package com.yangaobo.config;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
public class InternalAiFeignConfig {

    @Bean
    public RequestInterceptor internalAiTokenRequestInterceptor(
            @Value("${ai.internal-token:}") String internalToken) {
        return requestTemplate -> {
            if (StringUtils.hasText(internalToken)
                    && requestTemplate.path().startsWith("/internal/ai/")) {
                requestTemplate.header("X-Internal-Token", internalToken);
            }
        };
    }
}
