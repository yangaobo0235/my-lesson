package com.yangaobo.security;

import com.yangaobo.exception.ServiceException;
import com.yangaobo.result.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;

@Component
public class IdentityAuthenticationInterceptor implements HandlerInterceptor {
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final long MAX_TIMESTAMP_DIFFERENCE_MS = 60_000L;

    @Value("${ai.identity-secret:}")
    private String identitySecret;

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) {
        Long userId = parseLong(request.getHeader("X-User-Id"));
        Long timestamp = parseLong(request.getHeader("X-Request-Timestamp"));
        String username = decode(request.getHeader("X-Username"));
        String providedSignature = request.getHeader("X-Identity-Signature");

        if (userId == null
                || timestamp == null
                || !StringUtils.hasText(username)
                || !StringUtils.hasText(providedSignature)
                || !StringUtils.hasText(identitySecret)
                || Math.abs(System.currentTimeMillis() - timestamp) > MAX_TIMESTAMP_DIFFERENCE_MS) {
            throw new ServiceException(ResultCode.UNAUTHORIZED, "身份签名缺失或已过期");
        }

        String expectedSignature = sign(request.getMethod(), request.getRequestURI(), userId, timestamp);
        if (!MessageDigest.isEqual(
                expectedSignature.getBytes(StandardCharsets.US_ASCII),
                providedSignature.getBytes(StandardCharsets.US_ASCII))) {
            throw new ServiceException(ResultCode.UNAUTHORIZED, "身份签名校验失败");
        }

        AuthenticatedPrincipal principal = new AuthenticatedPrincipal(
                userId, username, parseRoles(request.getHeader("X-User-Roles")));
        SecurityContext.set(principal);

        if (handler instanceof HandlerMethod method
                && (method.hasMethodAnnotation(RequireAdmin.class)
                || method.getBeanType().isAnnotationPresent(RequireAdmin.class))
                && !principal.hasAnyRole(SecurityRoles.ADMIN_ROLES)) {
            SecurityContext.clear();
            throw new ServiceException(ResultCode.FORBIDDEN, "该操作需要管理员权限");
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler,
                                Exception exception) {
        SecurityContext.clear();
    }

    private String sign(String method, String path, Long userId, long timestamp) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(identitySecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(
                    (method + path + userId + timestamp).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("无法校验身份签名", exception);
        }
    }

    private List<String> parseRoles(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(this::decode)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private String decode(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private Long parseLong(String value) {
        try {
            return Long.valueOf(value);
        } catch (RuntimeException exception) {
            return null;
        }
    }
}
