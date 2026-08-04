package com.yangaobo.component;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TokenGlobalFilterTest {

    private final TokenGlobalFilter filter = new TokenGlobalFilter();
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(filter, "whiteList", List.of("user/loginByAccount"));
        ReflectionTestUtils.setField(filter, "identitySecret", "test-identity-secret");
        ReflectionTestUtils.setField(filter, "stringRedisTemplate", redisTemplate);
    }

    @Test
    void returnsUnauthorizedForExistingRoutesWithoutToken() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/user-server/api/v1/user/page").build()
        );

        filter.filter(exchange, ignored -> Mono.empty()).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        String body = exchange.getResponse().getBodyAsString().block();
        assertTrue(body.contains("\"code\":8000"));
    }

    @Test
    void returnsUnauthorizedForAiRoutesWithoutToken() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/ai/conversations").build()
        );

        filter.filter(exchange, ignored -> Mono.empty()).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    void doesNotAcceptTokenFromQueryString() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/ai/conversations?token=leaked-token").build()
        );

        filter.filter(exchange, ignored -> Mono.empty()).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    void whitelistRequiresAnExactPathSuffix() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get(
                        "/user-server/api/v1/user/loginByAccountUnexpected").build()
        );

        filter.filter(exchange, ignored -> Mono.empty()).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    void whitelistSupportsDynamicPathAndForwardsTrustedClientIp() {
        ReflectionTestUtils.setField(filter, "whiteList", List.of("user/getVcode"));
        AtomicReference<ServerWebExchange> forwardedExchange = new AtomicReference<>();
        GatewayFilterChain chain = forwarded -> {
            forwardedExchange.set(forwarded);
            return forwarded.getResponse().setComplete();
        };
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/user-server/api/v1/user/getVcode/13800138000")
                        .remoteAddress(new InetSocketAddress("127.0.0.1", 8080))
                        .build()
        );

        filter.filter(exchange, chain).block();

        assertEquals("127.0.0.1", forwardedExchange.get().getRequest()
                .getHeaders()
                .getFirst("X-Forwarded-For"));
    }

    @Test
    void whitelistDoesNotMatchSimilarDynamicPath() {
        ReflectionTestUtils.setField(filter, "whiteList", List.of("user/getVcode"));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get(
                        "/user-server/api/v1/user/getVcodeUnexpected/13800138000").build()
        );

        filter.filter(exchange, ignored -> Mono.empty()).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    void allowsPublicCourseSearchWithoutToken() {
        ReflectionTestUtils.setField(filter, "whiteList", List.of("course/search"));
        AtomicBoolean chainCalled = new AtomicBoolean();
        GatewayFilterChain chain = forwarded -> {
            chainCalled.set(true);
            return forwarded.getResponse().setComplete();
        };
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get(
                        "/course-server/api/v1/course/search?pageNum=1&pageSize=12&keyword=").build()
        );

        filter.filter(exchange, chain).block();

        assertTrue(chainCalled.get());
    }

    @Test
    void acceptsLegacyTokenJsonForExistingRoutes() {
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("legacy-token")).thenReturn(
                "{\"id\":1,\"username\":\"admin\",\"password\":\"legacy-value\"}"
        );
        AtomicBoolean chainCalled = new AtomicBoolean();
        AtomicReference<ServerWebExchange> forwardedExchange = new AtomicReference<>();
        GatewayFilterChain chain = forwarded -> {
            chainCalled.set(true);
            forwardedExchange.set(forwarded);
            return forwarded.getResponse().setComplete();
        };
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/order-server/api/v1/order/page")
                        .header("token", "legacy-token")
                        .header("X-User-Id", "999")
                        .build()
        );

        filter.filter(exchange, chain).block();

        assertTrue(chainCalled.get());
        assertEquals("1", forwardedExchange.get().getRequest()
                .getHeaders()
                .getFirst("X-User-Id"));
    }
}
