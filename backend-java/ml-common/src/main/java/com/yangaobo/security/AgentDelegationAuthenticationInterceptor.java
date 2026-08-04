package com.yangaobo.security;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.yangaobo.exception.ServiceException;
import com.yangaobo.result.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Component
public class AgentDelegationAuthenticationInterceptor
        implements HandlerInterceptor {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    @Value("${ai.identity-secret:}")
    private String identitySecret;

    @Value("${ai.internal-token:}")
    private String internalToken;

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler) {
        requireServiceToken(request.getHeader("X-Internal-Token"));
        SecurityContext.set(parseDelegation(
                request.getHeader("X-ML-Delegation")));
        return true;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception exception) {
        SecurityContext.clear();
    }

    private void requireServiceToken(String providedToken) {
        if (!StringUtils.hasText(internalToken)
                || !StringUtils.hasText(providedToken)
                || !MessageDigest.isEqual(
                        internalToken.getBytes(StandardCharsets.UTF_8),
                        providedToken.getBytes(StandardCharsets.UTF_8))) {
            throw new ServiceException(
                    ResultCode.FORBIDDEN,
                    "Agent服务身份校验失败");
        }
    }

    private AuthenticatedPrincipal parseDelegation(String token) {
        if (!StringUtils.hasText(token)
                || !StringUtils.hasText(identitySecret)) {
            throw unauthorized("用户委托令牌缺失");
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw unauthorized("用户委托令牌格式错误");
        }
        String signingInput = parts[0] + "." + parts[1];
        byte[] providedSignature;
        try {
            providedSignature = Base64.getUrlDecoder().decode(parts[2]);
        } catch (IllegalArgumentException exception) {
            throw unauthorized("用户委托令牌签名格式错误");
        }
        if (!MessageDigest.isEqual(hmac(signingInput), providedSignature)) {
            throw unauthorized("用户委托令牌签名错误");
        }
        try {
            JSONObject claims = JSONUtil.parseObj(new String(
                    Base64.getUrlDecoder().decode(parts[1]),
                    StandardCharsets.UTF_8));
            long now = Instant.now().getEpochSecond();
            Long expiresAt = claims.getLong("exp");
            Long issuedAt = claims.getLong("iat");
            if (!"ml-gateway".equals(claims.getStr("iss"))
                    || !"ml-agent".equals(claims.getStr("aud"))
                    || expiresAt == null
                    || issuedAt == null
                    || expiresAt < now
                    || issuedAt > now + 5
                    || expiresAt - issuedAt > 60
                    || !StringUtils.hasText(claims.getStr("jti"))) {
                throw unauthorized("用户委托令牌已过期或声明无效");
            }
            Long userId = Long.valueOf(claims.getStr("sub"));
            String username = claims.getStr("username");
            if (!StringUtils.hasText(username)) {
                throw unauthorized("用户委托令牌缺少用户名");
            }
            return new AuthenticatedPrincipal(
                    userId,
                    username,
                    parseRoles(claims.getJSONArray("roles")));
        } catch (ServiceException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw unauthorized("用户委托令牌声明格式错误");
        }
    }

    private List<String> parseRoles(JSONArray values) {
        if (values == null) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object value : values) {
            if (value != null && StringUtils.hasText(value.toString())) {
                result.add(value.toString());
            }
        }
        return List.copyOf(result);
    }

    private byte[] hmac(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(
                    identitySecret.getBytes(StandardCharsets.UTF_8),
                    HMAC_ALGORITHM));
            return mac.doFinal(value.getBytes(StandardCharsets.US_ASCII));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(
                    "无法校验Agent用户委托令牌",
                    exception);
        }
    }

    private ServiceException unauthorized(String message) {
        return new ServiceException(ResultCode.UNAUTHORIZED, message);
    }
}
