package com.yangaobo.config;

import com.yangaobo.security.AgentDelegationAuthenticationInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AgentDelegationWebMvcConfig implements WebMvcConfigurer {

    private final AgentDelegationAuthenticationInterceptor interceptor;

    public AgentDelegationWebMvcConfig(
            AgentDelegationAuthenticationInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor)
                .addPathPatterns("/internal/v1/agent/**");
    }
}
