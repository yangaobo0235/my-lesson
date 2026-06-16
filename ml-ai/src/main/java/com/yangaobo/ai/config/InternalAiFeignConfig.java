package com.yangaobo.ai.config;

import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import com.yangaobo.ai.exception.DownstreamServiceException;
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
            if (StringUtils.hasText(internalToken)) {
                requestTemplate.header("X-Internal-Token", internalToken);
            }
        };
    }

    @Bean
    public ErrorDecoder internalAiErrorDecoder() {
        return (methodKey, response) -> new DownstreamServiceException(
                "An internal business service is temporarily unavailable");
    }
}
