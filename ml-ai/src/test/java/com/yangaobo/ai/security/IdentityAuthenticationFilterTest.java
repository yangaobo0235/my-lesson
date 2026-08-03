package com.yangaobo.ai.security;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IdentityAuthenticationFilterTest {

    private static final String SECRET = "test-identity-secret";
    private final IdentityAuthenticationFilter filter = new IdentityAuthenticationFilter();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(filter, "identitySecret", SECRET);
        UserContext.clear();
    }

    @Test
    void rejectsMissingIdentityHeaders() throws ServletException, IOException {
        MockHttpServletRequest request = protectedRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(401, response.getStatus());
    }

    @Test
    void rejectsExpiredTimestamp() throws Exception {
        long timestamp = System.currentTimeMillis() - 61_000L;
        MockHttpServletRequest request = signedRequest(timestamp);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(401, response.getStatus());
    }

    @Test
    void acceptsValidSignatureAndClearsUserContext() throws Exception {
        MockHttpServletRequest request = signedRequest(System.currentTimeMillis());
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(200, response.getStatus());
        assertEquals(null, UserContext.get());
    }

    private MockHttpServletRequest protectedRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/ai/conversations");
        request.setRequestURI("/api/v1/ai/conversations");
        return request;
    }

    private MockHttpServletRequest signedRequest(long timestamp) throws Exception {
        MockHttpServletRequest request = protectedRequest();
        request.addHeader("X-User-Id", "1");
        request.addHeader("X-Username", "admin");
        request.addHeader("X-User-Roles", "%E7%AE%A1%E7%90%86%E5%91%98");
        request.addHeader("X-Request-Timestamp", Long.toString(timestamp));
        request.addHeader("X-Identity-Signature", sign(timestamp));
        return request;
    }

    private String sign(long timestamp) throws Exception {
        String payload = "GET/api/v1/ai/conversations1" + timestamp;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }
}
