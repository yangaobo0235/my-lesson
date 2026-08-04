package com.yangaobo.security;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import java.util.stream.Collectors;

@Component
public class BusinessIdentityFeignInterceptor implements RequestInterceptor {
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    @Value("${ai.identity-secret:}")
    private String identitySecret;

    @Override
    public void apply(RequestTemplate template) {
        AuthenticatedPrincipal principal;
        try {
            principal = SecurityContext.requirePrincipal();
        } catch (RuntimeException exception) {
            return;
        }
        if (identitySecret == null || identitySecret.isBlank()) {
            return;
        }

        long timestamp = System.currentTimeMillis();
        String path = template.path();
        template.header("X-User-Id", principal.id().toString());
        template.header("X-Username", encode(principal.username()));
        template.header("X-User-Roles", principal.roles().stream()
                .map(this::encode)
                .collect(Collectors.joining(",")));
        template.header("X-Request-Timestamp", Long.toString(timestamp));
        template.header("X-Identity-Signature",
                sign(template.method(), path, principal.id(), timestamp));
    }

    private String sign(String method, String path, Long userId, long timestamp) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(identitySecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(
                    (method + path + userId + timestamp).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("无法签名服务间身份", exception);
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
