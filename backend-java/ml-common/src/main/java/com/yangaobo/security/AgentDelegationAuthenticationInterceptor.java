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

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

@Component
public class AgentDelegationAuthenticationInterceptor
        implements HandlerInterceptor {

    @Value("${ai.delegation-public-keys:}")
    private String delegationPublicKeys;

    @Value("${ai.internal-token:}")
    private String internalToken;

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

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
                || !StringUtils.hasText(delegationPublicKeys)) {
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
        try {
            JSONObject header = JSONUtil.parseObj(new String(
                    Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8));
            if (!"RS256".equals(header.getStr("alg"))
                    || !StringUtils.hasText(header.getStr("kid"))) {
                throw unauthorized("用户委托令牌算法或密钥编号无效");
            }
            verify(signingInput, providedSignature, header.getStr("kid"));
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
            List<String> roles = parseRoles(claims.getJSONArray("roles"));
            if (!StringUtils.hasText(username) || roles.isEmpty()) {
                throw unauthorized("用户委托令牌缺少有效身份或权限");
            }
            requireActive(userId, claims.getStr("jti"));
            return new AuthenticatedPrincipal(
                    userId,
                    username,
                    roles);
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

    private void verify(String signingInput, byte[] providedSignature, String keyId) {
        try {
            PublicKey publicKey = publicKeys().get(keyId);
            if (publicKey == null) {
                throw unauthorized("用户委托令牌使用了未知密钥");
            }
            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(publicKey);
            verifier.update(signingInput.getBytes(StandardCharsets.US_ASCII));
            if (!verifier.verify(providedSignature)) {
                throw unauthorized("用户委托令牌签名错误");
            }
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(
                    "无法校验Agent用户委托令牌",
                    exception);
        }
    }

    private Map<String, PublicKey> publicKeys() throws GeneralSecurityException {
        Map<String, PublicKey> keys = new LinkedHashMap<>();
        for (String entry : delegationPublicKeys.split(";")) {
            int separator = entry.indexOf(':');
            if (separator <= 0 || separator == entry.length() - 1) {
                continue;
            }
            String keyId = entry.substring(0, separator).trim();
            String encoded = entry.substring(separator + 1)
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replace("\\n", "")
                    .replaceAll("\\s", "");
            keys.put(keyId, KeyFactory.getInstance("RSA").generatePublic(
                    new X509EncodedKeySpec(Base64.getDecoder().decode(encoded))));
        }
        return keys;
    }

    private void requireActive(Long userId, String jti) {
        if (redisTemplate == null) {
            return;
        }
        Boolean disabled = redisTemplate.hasKey("ml:delegation:user-disabled:" + userId);
        Boolean revoked = redisTemplate.hasKey("ml:delegation:revoked:" + jti);
        if (Boolean.TRUE.equals(disabled) || Boolean.TRUE.equals(revoked)) {
            throw unauthorized("用户已停用或委托令牌已吊销");
        }
    }

    private ServiceException unauthorized(String message) {
        return new ServiceException(ResultCode.UNAUTHORIZED, message);
    }
}
