package com.yangaobo.security;

import cn.hutool.json.JSONUtil;
import com.yangaobo.exception.ServiceException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.springframework.data.redis.core.StringRedisTemplate;

class AgentDelegationAuthenticationInterceptorTest {

    private static final KeyPair KEY_PAIR = keyPair();
    private static final String SERVICE_TOKEN =
            "test-service-token-with-at-least-32-bytes";

    private final AgentDelegationAuthenticationInterceptor interceptor =
            new AgentDelegationAuthenticationInterceptor();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(
                interceptor,
                "delegationPublicKeys",
                "test:" + Base64.getEncoder().encodeToString(KEY_PAIR.getPublic().getEncoded()));
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

    @Test
    void revokedDelegationJtiIsRejected() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.hasKey(org.mockito.ArgumentMatchers.startsWith(
                "ml:delegation:revoked:"))).thenReturn(true);
        ReflectionTestUtils.setField(interceptor, "redisTemplate", redisTemplate);
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET", "/internal/v1/agent/me/profile");
        request.addHeader("X-Internal-Token", SERVICE_TOKEN);
        request.addHeader("X-ML-Delegation", token(42L, "learner", List.of("STUDENT")));

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
                "alg", "RS256",
                "kid", "test",
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
        return input + "." + encode(sign(input));
    }

    private byte[] sign(String input) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(KEY_PAIR.getPrivate());
            signature.update(input.getBytes(StandardCharsets.US_ASCII));
            return signature.sign();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static KeyPair keyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private String encode(String value) {
        return encode(value.getBytes(StandardCharsets.UTF_8));
    }

    private String encode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
}
