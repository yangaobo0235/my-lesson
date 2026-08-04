package com.yangaobo.component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class InternalAiAuthInterceptor implements HandlerInterceptor {

    @Value("${ai.internal-token:}")
    private String internalToken;

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws IOException {
        String providedToken = request.getHeader("X-Internal-Token");
        if (StringUtils.hasText(internalToken)
                && StringUtils.hasText(providedToken)
                && MessageDigest.isEqual(
                        internalToken.getBytes(StandardCharsets.UTF_8),
                        providedToken.getBytes(StandardCharsets.UTF_8))) {
            return true;
        }

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json");
        response.getWriter().write(
                "{\"code\":8001,\"message\":\"Forbidden\",\"coderMessage\":\"Invalid internal AI token\"}"
        );
        return false;
    }
}
