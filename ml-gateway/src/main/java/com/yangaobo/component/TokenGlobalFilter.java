package com.yangaobo.component;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@RefreshScope
@Component
public class TokenGlobalFilter implements GlobalFilter, Ordered {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USERNAME_HEADER = "X-Username";
    private static final String USER_ROLES_HEADER = "X-User-Roles";
    private static final String TIMESTAMP_HEADER = "X-Request-Timestamp";
    private static final String SIGNATURE_HEADER = "X-Identity-Signature";
    private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";
    private static final List<String> IDENTITY_HEADERS = List.of(
            USER_ID_HEADER,
            USERNAME_HEADER,
            USER_ROLES_HEADER,
            TIMESTAMP_HEADER,
            SIGNATURE_HEADER,
            FORWARDED_FOR_HEADER
    );

    @Value("${token.white_list}")
    private List<String> whiteList;

    @Value("${ai.identity-secret:}")
    private String identitySecret;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest sanitizedRequest = sanitizeClientHeaders(exchange.getRequest());
        ServerWebExchange sanitizedExchange = exchange.mutate().request(sanitizedRequest).build();

        if (isWhite(sanitizedRequest)) {
            return chain.filter(sanitizedExchange);
        }

        ServerHttpResponse response = sanitizedExchange.getResponse();
        String token = getToken(sanitizedRequest);
        if (StrUtil.isBlank(token)) {
            return buildResponseData(response, HttpStatus.UNAUTHORIZED, 8000,
                    "登录过期", "请求未携带Token");
        }

        String tokenMessage = stringRedisTemplate.opsForValue().get(token);
        if (StrUtil.isBlank(tokenMessage)) {
            return buildResponseData(response, HttpStatus.UNAUTHORIZED, 8000,
                    "登录过期", "Redis中不存在该Token");
        }

        stringRedisTemplate.expire(token, 30, TimeUnit.MINUTES);
        TokenPrincipal principal = parsePrincipal(tokenMessage);
        if (principal == null) {
            return buildResponseData(response, HttpStatus.UNAUTHORIZED, 8000,
                    "登录过期", "Token身份数据无效，请重新登录");
        }
        if (StrUtil.isBlank(identitySecret)) {
            return buildResponseData(response, HttpStatus.SERVICE_UNAVAILABLE, 9000,
                    "身份服务不可用", "网关未配置ai.identity-secret");
        }

        long timestamp = System.currentTimeMillis();
        String method = sanitizedRequest.getMethod().name();
        String path = downstreamPath(sanitizedRequest.getURI().getRawPath());
        String signature = sign(method, path, principal.id(), timestamp);

        ServerHttpRequest authenticatedRequest = sanitizedRequest.mutate()
                .headers(headers -> {
                    addIdentityHeaders(headers, principal, timestamp, signature);
                })
                .build();
        return chain.filter(sanitizedExchange.mutate().request(authenticatedRequest).build());
    }

    @Override
    public int getOrder() {
        return 0;
    }

    private ServerHttpRequest sanitizeClientHeaders(ServerHttpRequest request) {
        return request.mutate()
                .headers(headers -> {
                    IDENTITY_HEADERS.forEach(headers::remove);
                    if (request.getRemoteAddress() != null) {
                        headers.set(FORWARDED_FOR_HEADER,
                                request.getRemoteAddress().getAddress().getHostAddress());
                    }
                })
                .build();
    }

    private void addIdentityHeaders(HttpHeaders headers,
                                    TokenPrincipal principal,
                                    long timestamp,
                                    String signature) {
        headers.set(USER_ID_HEADER, principal.id().toString());
        headers.set(USERNAME_HEADER, encodeHeaderValue(principal.username()));
        headers.set(USER_ROLES_HEADER, principal.roles().stream()
                .map(this::encodeHeaderValue)
                .collect(Collectors.joining(",")));
        headers.set(TIMESTAMP_HEADER, Long.toString(timestamp));
        headers.set(SIGNATURE_HEADER, signature);
    }

    private TokenPrincipal parsePrincipal(String tokenMessage) {
        try {
            JSONObject json = JSONUtil.parseObj(tokenMessage);
            Long id = json.getLong("id");
            String username = json.getStr("username");
            JSONArray roleArray = json.getJSONArray("roles");
            if (id == null || StrUtil.isBlank(username)) {
                return null;
            }

            List<String> roles = new ArrayList<>();
            if (roleArray != null) {
                for (Object role : roleArray) {
                    if (role != null && StrUtil.isNotBlank(role.toString())) {
                        roles.add(role.toString());
                    }
                }
            }
            return new TokenPrincipal(id, username, List.copyOf(roles));
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private String sign(String method, String path, Long userId, long timestamp) {
        String payload = method + path + userId + timestamp;
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(identitySecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to create identity signature", exception);
        }
    }

    private String downstreamPath(String rawPath) {
        int secondSlash = rawPath.indexOf('/', 1);
        if (secondSlash < 0) {
            return rawPath;
        }
        String routePrefix = rawPath.substring(1, secondSlash);
        if (routePrefix.equals("user-server")
                || routePrefix.equals("course-server")
                || routePrefix.equals("sale-server")
                || routePrefix.equals("order-server")) {
            return rawPath.substring(secondSlash);
        }
        return rawPath;
    }

    private String encodeHeaderValue(String value) {
        String sanitized = value == null ? "" : value.replace("\r", "").replace("\n", "");
        return URLEncoder.encode(sanitized, StandardCharsets.UTF_8);
    }

    private Mono<Void> buildResponseData(ServerHttpResponse response,
                                         HttpStatus status,
                                         int code,
                                         String message,
                                         String coderMessage) {
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> resultMap = Map.of(
                "code", code,
                "message", message,
                "coderMessage", coderMessage
        );
        byte[] result = JSONUtil.toJsonStr(resultMap).getBytes(StandardCharsets.UTF_8);
        return response.writeWith(Flux.just(response.bufferFactory().wrap(result)));
    }

    private boolean isWhite(ServerHttpRequest request) {
        String path = downstreamPath(request.getURI().getRawPath());
        return whiteList.stream()
                .map(String::trim)
                .filter(StrUtil::isNotBlank)
                .map(item -> item.startsWith("/") ? item.substring(1) : item)
                .map(item -> "/api/v1/" + item)
                .anyMatch(item -> path.equals(item) || path.startsWith(item + "/"));
    }

    private String getToken(ServerHttpRequest request) {
        return request.getHeaders().getFirst("token");
    }

    private record TokenPrincipal(Long id, String username, List<String> roles) {
    }
}
