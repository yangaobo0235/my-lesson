package com.yangaobo.servicedesk.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 通过临时下游验证实际路由、入口清理与错误，不代表业务联调完成。
 *
 * @author yangaobo
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"management.tracing.enabled=true", "management.tracing.sampling.probability=1.0"})
@AutoConfigureObservability
@ActiveProfiles({"dev", "test"})
class GatewayRoutesTest {

    /**
     * 当前测试独占的有界HTTP下游。
     */
    private static HttpServer upstream;

    /**
     * 网关随机端口。
     */
    @LocalServerPort
    private int port;

    /**
     * Boot真实JSON配置。
     */
    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 所有正式路由都指向临时下游，禁止访问共享真实业务环境。
     *
     * @param registry 当前测试动态配置
     * @throws IOException 临时监听建立失败
     */
    @DynamicPropertySource
    static void configureUpstream(DynamicPropertyRegistry registry) throws IOException {
        upstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        upstream.createContext(
                "/",
                exchange -> {
                    exchange.getResponseHeaders().set("X-Trace-Id", "c".repeat(32));
                    for (String header :
                            new String[]{
                                    "X-User-Id", "X-Internal-Token", "traceparent", "baggage", "Cookie"
                            }) {
                        String value = exchange.getRequestHeaders().getFirst(header);
                        if (value != null) {
                            exchange.getResponseHeaders().set("Received-" + header, value);
                        }
                    }
                    byte[] body =
                            exchange.getRequestURI().getPath().getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200, body.length);
                    try (var output = exchange.getResponseBody()) {
                        output.write(body);
                    }
                });
        upstream.start();
        String uri = "http://127.0.0.1:" + upstream.getAddress().getPort();
        registry.add("management.zipkin.tracing.endpoint", () -> uri + "/api/v2/spans");
        for (String service : new String[]{"user", "ticket", "knowledge", "ai"}) {
            registry.add("desk.downstream." + service, () -> uri);
        }
    }

    /**
     * 关闭当前测试拥有的监听，不停止其他进程。
     */
    @AfterAll
    static void stopUpstream() {
        if (upstream != null) {
            upstream.stop(0);
        }
    }

    /**
     * 所有公开资源与管理白名单保留原路径；未登记资源无转发旁路。
     *
     * @throws Exception 请求或JSON核对失败
     */
    @Test
    void forwardsOnlyRegisteredPaths() throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            for (String path :
                    new String[]{
                            "/api/v1/users/probe",
                            "/api/v1/tickets/probe",
                            "/api/v1/attachments/by-request/probe",
                            "/api/v1/knowledge/probe",
                            "/api/v1/ai/probe",
                            "/api/v1/admin/organization/query",
                            "/api/v1/admin/account/list",
                            "/api/v1/admin/audit-event/list",
                            "/api/v1/admin/product-model/list",
                            "/api/v1/admin/service-item/list",
                            "/api/v1/admin/service-entitlement/query",
                            "/api/v1/admin/device/query",
                            "/api/v1/admin/onsite-slot/list",
                            "/api/v1/admin/onsite-booking/query",
                            "/api/v1/admin/document-series/list",
                            "/api/v1/admin/document/query",
                            "/api/v1/admin/document-event/list",
                            "/api/v1/admin/index-job/query",
                            "/api/v1/admin/retrieval/diagnostics"
                    }) {
                HttpResponse<String> response =
                        client.send(request(path).build(), HttpResponse.BodyHandlers.ofString());
                assertEquals(200, response.statusCode(), path);
                assertEquals(path, response.body(), path);
                assertEquals(
                        java.util.List.of("c".repeat(32)),
                        response.headers().allValues("X-Trace-Id"),
                        path);
            }
            for (String path :
                    new String[]{
                            "/api/v1/admin/service-item-extra/query",
                            "/api/v1/admin/service-entitlements/query",
                            "/api/v1/admin/unknown/query",
                            "/api/v1/admin/feedback/query",
                            "/internal/v1/users/probe",
                            "/v3/api-docs",
                            "/swagger-ui.html",
                            "/api/v1/admin/accounting/list"
                    }) {
                HttpResponse<String> response =
                        client.send(request(path).build(), HttpResponse.BodyHandlers.ofString());
                assertEquals(404, response.statusCode(), path);
                JsonNode result = objectMapper.readTree(response.body());
                assertEquals(1002, result.get("code").intValue());
                assertTrue(result.get("data").isNull());
                assertEquals(
                        response.headers().firstValue("X-Trace-Id").orElseThrow(),
                        result.get("traceId").textValue());
            }
        }
    }

    /**
     * 浏览器不能提交可信身份或追踪头，但会话Cookie保持原协议。
     *
     * @throws IOException          请求失败
     * @throws InterruptedException 等待中断
     */
    @Test
    void stripsUntrustedIdentityAndTraceHeaders() throws IOException, InterruptedException {
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpRequest request =
                    request("/api/v1/users/probe")
                            .header("X-User-Id", "forged")
                            .header("X-Internal-Token", "forged")
                            .header(
                                    "traceparent",
                                    "00-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa-bbbbbbbbbbbbbbbb-01")
                            .header("baggage", "private=value")
                            .header("Cookie", "DESK_SESSION=test-session")
                            .build();
            HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
            for (String header :
                    new String[]{"X-User-Id", "X-Internal-Token", "baggage"}) {
                assertFalse(
                        response.headers().firstValue("Received-" + header).isPresent(), header);
            }
            String traceparent = response.headers().firstValue("Received-traceparent").orElseThrow();
            assertTrue(traceparent.matches("00-[0-9a-f]{32}-[0-9a-f]{16}-01"));
            assertFalse(traceparent.contains("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"),
                    "真实观测必须生成新链，不能先继承外部头再仅删除原始字段");
            assertEquals(
                    "DESK_SESSION=test-session",
                    response.headers().firstValue("Received-Cookie").orElseThrow());
        }
    }

    /**
     * 验证网关运行树没有被common或api引入MVC及数据库栈。
     */
    @Test
    void keepsReactiveClasspathIsolated() {
        assertThrows(
                ClassNotFoundException.class,
                () -> Class.forName("org.springframework.web.servlet.DispatcherServlet"));
        assertThrows(
                ClassNotFoundException.class,
                () -> Class.forName("com.baomidou.mybatisplus.core.mapper.BaseMapper"));
    }

    /**
     * 创建有界请求。
     *
     * @param path 目标路径
     * @return 请求构造器
     */
    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(10))
                .GET();
    }
}
