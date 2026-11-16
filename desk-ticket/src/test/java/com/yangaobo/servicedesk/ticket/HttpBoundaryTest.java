package com.yangaobo.servicedesk.ticket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangaobo.servicedesk.common.base.CommonErrorCode;
import com.yangaobo.servicedesk.common.base.ErrorCode;
import com.yangaobo.servicedesk.common.base.Result;
import com.yangaobo.servicedesk.common.exception.BusinessException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 通过真实HTTP验证所属MVC应用的协议边界，不安装业务探针到生产源码。
 *
 * @author yangaobo
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"dev", "test"})
@Import({HttpBoundaryTest.BoundaryProbe.class, HttpBoundaryTest.SchemaProbe.class})
class HttpBoundaryTest {

    /**
     * 随机隔离端口。
     */
    @LocalServerPort
    private int port;

    /**
     * 所属应用的真实JSON配置。
     */
    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 验证201及响应头保留、业务成功0和统一追踪标识。
     *
     * @throws Exception HTTP或JSON检查失败
     */
    @Test
    void preservesCreationStatusPayloadAndTrace() throws Exception {
        HttpResponse<String> response = request("GET", "/__test/created");
        JsonNode result = objectMapper.readTree(response.body());
        assertEquals(201, response.statusCode());
        assertEquals("/created", response.headers().firstValue("Location").orElseThrow());
        assertEquals(0, result.get("code").intValue());
        assertEquals("payload", result.get("data").textValue());
        assertEquals(
                response.headers().firstValue("X-Trace-Id").orElseThrow(),
                result.get("traceId").textValue());
    }

    /**
     * 验证框架404/405、Allow与null字段，不返回默认错误格式。
     *
     * @throws Exception HTTP或JSON检查失败
     */
    @Test
    void preservesFrameworkFailuresAndHeaders() throws Exception {
        HttpResponse<String> missing = request("GET", "/api/v1/not-implemented");
        assertEquals(404, missing.statusCode());
        JsonNode result = objectMapper.readTree(missing.body());
        assertEquals(1002, result.get("code").intValue());
        assertTrue(result.has("data") && result.get("data").isNull());
        HttpResponse<String> method = request("POST", "/__test/created");
        assertEquals(405, method.statusCode());
        assertEquals(1006, objectMapper.readTree(method.body()).get("code").intValue());
        assertTrue(method.headers().firstValue("Allow").orElseThrow().contains("GET"));
    }

    /**
     * 已登记拒绝准确映射，未知异常和未知错误类型安全回退。
     *
     * @throws Exception HTTP或JSON检查失败
     */
    @Test
    void distinguishesKnownRefusalAndUnsafeFailures() throws Exception {
        HttpResponse<String> busy = request("GET", "/__test/busy");
        assertEquals(503, busy.statusCode());
        assertEquals(1011, objectMapper.readTree(busy.body()).get("code").intValue());
        for (String path : new String[]{"/__test/unsafe", "/__test/unknown-code"}) {
            HttpResponse<String> unsafe = request("GET", path);
            assertEquals(500, unsafe.statusCode());
            assertEquals(1, objectMapper.readTree(unsafe.body()).get("code").intValue());
            assertFalse(unsafe.body().contains("secret-value"));
        }
    }

    /**
     * 原生健康与开发文档不包Result，OpenAPI实际版本为3.0。
     *
     * @throws Exception HTTP或JSON检查失败
     */
    @Test
    void keepsHealthAndOpenApiNative() throws Exception {
        JsonNode health = objectMapper.readTree(request("GET", "/actuator/health").body());
        assertEquals("UP", health.get("status").textValue());
        assertFalse(health.has("code"));
        HttpResponse<String> documentation = request("GET", "/v3/api-docs");
        assertEquals(200, documentation.statusCode());
        JsonNode api = objectMapper.readTree(documentation.body());
        assertTrue(api.get("openapi").textValue().startsWith("3.0."));
        assertFalse(api.has("code"));
    }

    /**
     * 使用有界真实HTTP访问随机端口。
     *
     * @param method 请求方法
     * @param path   测试路径
     * @return 原始HTTP响应
     * @throws IOException          请求失败
     * @throws InterruptedException 等待中断
     */
    private HttpResponse<String> request(String method, String path)
            throws IOException, InterruptedException {
        try (HttpClient client =
                     HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            HttpRequest request =
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                            .timeout(Duration.ofSeconds(10))
                            .method(method, HttpRequest.BodyPublishers.noBody())
                            .build();
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }

    /**
     * 只在当前测试应用注册的边界样例。
     *
     * @author yangaobo
     */
    @RestController
    @RequestMapping("/__test")
    static class BoundaryProbe {

        /**
         * 返回带状态及头的成功，用于验证Advice不改写协议。
         *
         * @return 创建成功响应
         */
        @GetMapping("/created")
        public ResponseEntity<Result<String>> created() {
            return ResponseEntity.created(URI.create("/created")).body(Result.success("payload"));
        }

        /**
         * 触发已登记的依赖不可用拒绝。
         *
         * @return 不会正常返回
         */
        @GetMapping("/busy")
        public Result<Void> busy() {
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE);
        }

        /**
         * 验证未知异常中的敏感文本不向浏览器公开。
         *
         * @return 不会正常返回
         */
        @GetMapping("/unsafe")
        public Result<Void> unsafe() {
            throw new IllegalStateException("secret-value");
        }

        /**
         * 验证未登记错误不能伪装成可信业务拒绝。
         *
         * @return 不会正常返回
         */
        @GetMapping("/unknown-code")
        public Result<Void> unknownCode() {
            throw new BusinessException(
                    new ErrorCode() {
                        /** {@inheritDoc} */
                        @Override
                        public int code() {
                            return 9999;
                        }

                        /** {@inheritDoc} */
                        @Override
                        public String message() {
                            return "secret-value";
                        }
                    });
        }
    }

    /**
     * 生成文档须表达真实线值、历史输入子集、精度及同时需要的安全要素。
     */
    @Test
    void openApiDescribesActualWireContract() throws Exception {
        var api = objectMapper.readTree(request("GET", "/v3/api-docs").body());
        var schemes = api.path("components").path("securitySchemes");
        assertEquals("DESK_SESSION", schemes.path("Session").path("name").asText());
        assertEquals("cookie", schemes.path("Session").path("in").asText());
        assertEquals("X-CSRF-TOKEN", schemes.path("CSRF").path("name").asText());
        var security =
                api.path("paths").path("/api/v1/__test/schema").path("post").path("security");
        assertTrue(security.get(0).has("Session") && security.get(0).has("CSRF"));
        var schemas = api.path("components").path("schemas");
        var input = schemas.path("TicketResolveForm").path("properties");
        var resolution = input.path("resolutionType");
        assertEquals(3, resolution.path("enum").size());
        assertFalse(resolution.path("enum").toString().contains("LEGACY_UNKNOWN"));
        assertTrue(resolution.path("description").asText().contains("GUIDANCE"));
        assertEquals("^[0-9a-f]{32}$", input.path("sourceDraftId").path("pattern").asText());
        var iterator = schemas.elements();
        boolean pageFound = false;
        while (iterator.hasNext()) {
            var properties = iterator.next().path("properties");
            if (properties.has("total") && properties.has("records")) {
                assertEquals("string", properties.path("total").path("type").asText());
                pageFound = true;
            }
        }
        assertTrue(pageFound);
    }

    /**
     * 仅在测试文档中暴露真实模型，不启动业务数据库。
     *
     * @author yangaobo
     */
    @RestController
    static class SchemaProbe {
        /**
         * 返回实际泛型响应，只用于生成接口文档。
         */
        @org.springframework.web.bind.annotation.PostMapping("/api/v1/__test/schema")
        public com.yangaobo.servicedesk.common.base.Result<
                com.yangaobo.servicedesk.common.base.PageVO<
                        com.yangaobo.servicedesk.ticket.model.vo.ticket.TicketVO>>
        schema(
                @org.springframework.web.bind.annotation.RequestBody
                com.yangaobo.servicedesk.ticket.model.form.ticket.TicketResolveForm
                        form) {
            return com.yangaobo.servicedesk.common.base.Result.success(null);
        }
    }
}
