package com.yangaobo.config;

import com.yangaobo.security.IdentityAuthenticationInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class IdentitySecurityWebMvcConfig implements WebMvcConfigurer {
    private final IdentityAuthenticationInterceptor interceptor;

    public IdentitySecurityWebMvcConfig(IdentityAuthenticationInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor)
                .addPathPatterns("/api/v1/**")
                .excludePathPatterns(
                        "/api/v1/user/loginByAccount",
                        "/api/v1/user/loginByPhone",
                        "/api/v1/user/getVcode/**",
                        "/api/v1/user/insert",
                        "/api/v1/banner/top/**",
                        "/api/v1/article/top/**",
                        "/api/v1/notice/top/**",
                        "/api/v1/seckill/near/**",
                        "/api/v1/course/page",
                        "/api/v1/course/search",
                        "/api/v1/course/select/**",
                        "/api/v1/order/prePayNotify"
                );
    }
}
