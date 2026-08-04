package com.yangaobo.config;

import com.yangaobo.component.InternalAiAuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class InternalAiWebMvcConfig implements WebMvcConfigurer {

    private final InternalAiAuthInterceptor internalAiAuthInterceptor;

    public InternalAiWebMvcConfig(InternalAiAuthInterceptor internalAiAuthInterceptor) {
        this.internalAiAuthInterceptor = internalAiAuthInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(internalAiAuthInterceptor)
                .addPathPatterns("/internal/ai/**");
    }
}
