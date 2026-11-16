package com.yangaobo.servicedesk.user.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.stereotype.Component;

/**
 * 会话绑定CSRF令牌的业务边界。
 *
 * @author yangaobo
 */
@Component
@ConditionalOnProperty(name = "desk.identity.enabled", havingValue = "true", matchIfMissing = true)
public class SessionCsrfTokenRepository implements CsrfTokenRepository {
    private final byte[] secret;

    /**
     * 令牌由会话标识与独立密钥派生，密钥缺失拒绝启动。
     */
    public SessionCsrfTokenRepository(@Value("${desk.identity.csrf-secret}") String secret) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32)
            throw new IllegalArgumentException("CSRF密钥至少32字节");
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 会话轮换自然使旧令牌失效，不把令牌落入JDBC Session。
     */
    @Override
    public CsrfToken generateToken(HttpServletRequest request) {
        return token(request.getSession(true).getId());
    }

    /**
     * 无持久副本；删除会话或轮换标识即撤销令牌。
     */
    @Override
    public void saveToken(
            CsrfToken token, HttpServletRequest request, HttpServletResponse response) {
    }

    /**
     * 匿名首次请求由框架延迟生成会话与令牌。
     */
    @Override
    public CsrfToken loadToken(HttpServletRequest request) {
        return request.getSession(false) == null ? null : token(request.getSession(false).getId());
    }

    /**
     * 内部身份核验复用同一CSRF规则，仅在user边界处理原会话。
     */
    public boolean matches(String sessionId, String presented) {
        return presented != null
                && MessageDigest.isEqual(
                token(sessionId).getToken().getBytes(StandardCharsets.UTF_8),
                presented.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 使用HMAC，不将会话标识直接暴露为CSRF令牌。
     */
    private CsrfToken token(String sessionId) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return new DefaultCsrfToken(
                    "X-CSRF-TOKEN",
                    "_csrf",
                    Base64.getUrlEncoder()
                            .withoutPadding()
                            .encodeToString(
                                    mac.doFinal(sessionId.getBytes(StandardCharsets.UTF_8))));
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException(failure);
        }
    }
}
