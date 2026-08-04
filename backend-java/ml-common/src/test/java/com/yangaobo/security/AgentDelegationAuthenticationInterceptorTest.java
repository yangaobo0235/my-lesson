package com.yangaobo.security;

import cn.hutool.json.JSONUtil;
import com.yangaobo.exception.ServiceException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentDelegationAuthenticationInterceptorTest {

    private static final String IDENTITY_SECRET =
            "test-identity-secret-with-at-least-32-bytes";
    private static final String SERVICE_TOKEN =
            "test-service-token-with-at-least-32-bytes";

    private final AgentDelegationAuthenticationInterceptor interceptor =
            new AgentDelegationAuthenticationInterceptor();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(
                interceptor,
                "identitySecret",
                IDENTITY_SECRET);
        ReflectionTestUtils.setField(
                interceptor,
                "internalToken",
                SERVICE_TOKEN);
    }

    @AfterEach
    void clear() {
        SecurityContext.clear();
    }

    @Test
    void validDelegationEstablishesTrustedSecurityContext() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/v1/agent/me/profile");
        request.addHeader("X-Internal-Token", SERVICE_TOKEN);
        request.addHeader("X-ML-Delegation", token(42L, "learner", List.of("STUDENT")));

        interceptor.preHandle(
                request,
                new MockHttpServletResponse(),
                new Object());

        assertEquals(42L, SecurityContext.requireUserId());
        assertEquals("learner", SecurityContext.requirePrincipal().username());
    }

    @Test
    void invalidServiceTokenIsRejectedBeforeDelegation() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/v1/agent/me/profile");
        request.addHeader("X-Internal-Token", "wrong-token");
        request.addHeader("X-ML-Delegation", token(42L, "learner", List.of()));

        assertThrows(
                ServiceException.class,
                () -> interceptor.preHandle(
                        request,
                        new MockHttpServletResponse(),
                        new Object()));
    }

    private String token(Long userId, String username, List<String> roles) {
        long now = Instant.now().getEpochSecond();
        String header = encode(JSONUtil.toJsonStr(Map.of(
                "alg", "HS256",
                "typ", "JWT")));
        String claims = encode(JSONUtil.toJsonStr(Map.of(
                "iss", "ml-gateway",
                "aud", "ml-agent",
                "sub", userId.toString(),
                "username", username,
                "roles", roles,
                "iat", now,
                "exp", now + 60,
                "jti", UUID.randomUUID().toString())));
        String input = header + "." + claims;
        return input + "." + encode(hmac(input));
    }

    private byte[] hmac(String input) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    IDENTITY_SECRET.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"));
            return mac.doFinal(input.getBytes(StandardCharsets.US_ASCII));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String encode(String value) {
        return encode(value.getBytes(StandardCharsets.UTF_8));
    }

    private String encode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
}
