package com.yangaobo.servicedesk.user.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangaobo.servicedesk.common.base.CommonErrorCode;
import com.yangaobo.servicedesk.common.base.ErrorCode;
import com.yangaobo.servicedesk.common.base.Result;
import com.yangaobo.servicedesk.user.component.web.HttpErrorMapping;
import com.yangaobo.servicedesk.user.component.web.RequestTraceFilter;
import com.yangaobo.servicedesk.user.error.AuthenticationErrorCode;
import com.yangaobo.servicedesk.user.security.SessionCsrfTokenRepository;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

/**
 * 会话安全链的业务边界。
 *
 * @author yangaobo
 */
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(name = "desk.identity.enabled", havingValue = "true", matchIfMissing = true)
public class IdentitySecurityConfig {
    private final ObjectMapper objectMapper;
    private final SessionCsrfTokenRepository csrfTokens;

    /** 仅内部接口使用独立的服务认证，不读取浏览器安全上下文。 */
    @Bean
    @Order(1)
    public SecurityFilterChain internalSecurity(HttpSecurity http) throws Exception {
        return http.securityMatcher("/internal/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .build();
    }

    /** 同源浏览器入口使用Spring Security CSRF及显式保存的会话上下文。 */
    @Bean
    @Order(2)
    public SecurityFilterChain browserSecurity(HttpSecurity http) throws Exception {
        return http.csrf(
                        csrf ->
                                csrf.csrfTokenRepository(csrfTokens)
                                        .csrfTokenRequestHandler(
                                                new CsrfTokenRequestAttributeHandler()))
                .requestCache(cache -> cache.disable())
                .authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers("/api/v1/admin/**")
                                        .authenticated()
                                        .anyRequest()
                                        .permitAll())
                .exceptionHandling(
                        errors ->
                                errors.authenticationEntryPoint(
                                                (request, response, failure) ->
                                                        writeError(
                                                                request,
                                                                response,
                                                                AuthenticationErrorCode
                                                                        .UNAUTHENTICATED))
                                        .accessDeniedHandler(
                                                (request, response, failure) ->
                                                        writeError(
                                                                request,
                                                                response,
                                                                CommonErrorCode.FORBIDDEN)))
                .build();
    }

    /** Cookie只在同源根路径发送，生产强制Secure。 */
    @Bean
    public CookieSerializer cookieSerializer(
            @Value("${desk.identity.secure-cookie:false}") boolean secure) {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieName("DESK_SESSION");
        serializer.setCookiePath("/");
        serializer.setUseHttpOnlyCookie(true);
        serializer.setUseSecureCookie(secure);
        serializer.setSameSite("Lax");
        serializer.setUseBase64Encoding(false);
        return serializer;
    }

    /** 安全链输出与MVC一致的错误，避免重定向登录页。 */
    private void writeError(
            HttpServletRequest request, HttpServletResponse response, ErrorCode error)
            throws java.io.IOException {
        response.setStatus(HttpErrorMapping.statusOf(error).value());
        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(
                response.getOutputStream(),
                Result.failure(error, RequestTraceFilter.traceId(request)));
    }
}
