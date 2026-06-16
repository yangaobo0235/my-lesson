package com.yangaobo.ai.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class IdentityAuthenticationFilter extends OncePerRequestFilter {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final long MAX_TIMESTAMP_DIFFERENCE_MS = 60_000L;

    @Value("${ai.identity-secret:}")
    private String identitySecret;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v1/ai/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String userIdHeader = request.getHeader("X-User-Id");
        String username = decodeHeaderValue(request.getHeader("X-Username"));
        String rolesHeader = request.getHeader("X-User-Roles");
        String timestampHeader = request.getHeader("X-Request-Timestamp");
        String providedSignature = request.getHeader("X-Identity-Signature");

        Long userId = parseLong(userIdHeader);
        Long timestamp = parseLong(timestampHeader);
        if (userId == null
                || timestamp == null
                || !StringUtils.hasText(username)
                || !StringUtils.hasText(providedSignature)
                || !StringUtils.hasText(identitySecret)
                || Math.abs(System.currentTimeMillis() - timestamp) > MAX_TIMESTAMP_DIFFERENCE_MS) {
            writeUnauthorized(response);
            return;
        }

        String expectedSignature = sign(
                request.getMethod(),
                request.getRequestURI(),
                userId,
                timestamp
        );
        if (!constantTimeEquals(expectedSignature, providedSignature)) {
            writeUnauthorized(response);
            return;
        }

        UserContext.set(new AuthenticatedUser(userId, username, parseRoles(rolesHeader)));
        try {
            filterChain.doFilter(request, response);
        } finally {
            UserContext.clear();
        }
    }

    private String sign(String method, String path, Long userId, long timestamp) {
        String payload = method + path + userId + timestamp;
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(identitySecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to verify identity signature", exception);
        }
    }

    private boolean constantTimeEquals(String expected, String provided) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII),
                provided.getBytes(StandardCharsets.US_ASCII)
        );
    }

    private Long parseLong(String value) {
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private List<String> parseRoles(String rolesHeader) {
        if (!StringUtils.hasText(rolesHeader)) {
            return List.of();
        }
        return Arrays.stream(rolesHeader.split(","))
                .map(this::decodeHeaderValue)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private String decodeHeaderValue(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json");
        response.getWriter().write(
                "{\"code\":8000,\"message\":\"Unauthorized\",\"coderMessage\":\"Invalid identity signature\"}"
        );
    }
}
