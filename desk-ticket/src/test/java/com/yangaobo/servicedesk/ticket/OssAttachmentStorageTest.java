package com.yangaobo.servicedesk.ticket;

import com.aliyun.sdk.service.oss2.OSSClient;
import com.aliyun.sdk.service.oss2.credentials.StaticCredentialsProvider;
import com.sun.net.httpserver.HttpServer;
import com.yangaobo.servicedesk.common.exception.BusinessException;
import com.yangaobo.servicedesk.ticket.component.DiagnosticText;
import com.yangaobo.servicedesk.ticket.component.OssAttachmentStorage;
import com.yangaobo.servicedesk.ticket.config.AttachmentStorageProperties;
import com.yangaobo.servicedesk.ticket.model.entity.AttachmentEntity;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 真实 OSS SDK 对本地 HTTP 替身验证禁止覆盖、精确版本及不隐式重试，不代表云端联调。
 *
 * @author yangaobo
 */
class OssAttachmentStorageTest {
    /**
     * SDK 真实请求带禁止覆盖和固定版本参数，摘要不符拒绝交付。
     */
    @Test
    void bindsImmutableObjectAndExactVersion() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> overwrite = new AtomicReference<>();
        AtomicReference<String> query = new AtomicReference<>();
        byte[] bytes = "校验文本".getBytes(StandardCharsets.UTF_8);
        server.createContext("/", exchange -> {
            overwrite.compareAndSet(null, exchange.getRequestHeaders().getFirst("x-oss-forbid-overwrite"));
            query.set(exchange.getRequestURI().getQuery());
            exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().set("x-oss-version-id", "version-fixed");
            exchange.getResponseHeaders().set("x-oss-request-id", "test-request");
            exchange.sendResponseHeaders(200, "GET".equals(exchange.getRequestMethod()) ? bytes.length : -1);
            if ("GET".equals(exchange.getRequestMethod())) exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try (var client = client(server)) {
            var adapter = new OssAttachmentStorage(client, properties());
            var value = attachment(bytes);
            value.setStorageVersionId(adapter.put(value, bytes));
            assertEquals("true", overwrite.get());
            assertArrayEquals(bytes, adapter.read(value));
            assertEquals("versionId=version-fixed", query.get());
            value.setFileSha256("0".repeat(64));
            assertThrows(BusinessException.class, () -> adapter.read(value));
            value.setStorageKey("knowledge/ci/other");
            assertThrows(BusinessException.class, () -> adapter.put(value, bytes));
        } finally {
            server.stop(0);
        }
    }

    /**
     * 未知外写及无版本回执不能再次 PUT 或被当成成功。
     */
    @Test
    void noAutomaticRetryAfterRemoteFailure() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger calls = new AtomicInteger();
        server.createContext("/", exchange -> {
            calls.incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            byte[] error = "<Error><Code>InternalError</Code><Message>fixture</Message><RequestId>fixture</RequestId></Error>".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(500, error.length);
            exchange.getResponseBody().write(error);
            exchange.close();
        });
        server.start();
        try (var client = client(server)) {
            var adapter = new OssAttachmentStorage(client, properties());
            byte[] bytes = "text".getBytes(StandardCharsets.UTF_8);
            assertThrows(BusinessException.class, () -> adapter.put(attachment(bytes), bytes));
            assertEquals(1, calls.get());
        } finally {
            server.stop(0);
        }
    }

    /**
     * 测试只连接本机随机端口，凭据为无权限固定夹具。
     */
    private OSSClient client(HttpServer server) {
        return OSSClient.newBuilder().endpoint("http://127.0.0.1:" + server.getAddress().getPort())
                .region("cn-test").credentialsProvider(new StaticCredentialsProvider("fixture", "fixture"))
                .connectTimeout(Duration.ofSeconds(1)).readWriteTimeout(Duration.ofSeconds(2))
                .retryMaxAttempts(1).enabledRedirect(false).proxyFromEnvironment(false).build();
    }

    /**
     * 独立 ticket 前缀夹具。
     */
    private AttachmentStorageProperties properties() {
        var value = new AttachmentStorageProperties();
        value.setBucket("ticket-private");
        value.setObjectPrefix("ticket/ci/attachments/");
        return value;
    }

    /**
     * 固定原件身份及摘要。
     */
    private AttachmentEntity attachment(byte[] bytes) {
        var value = new AttachmentEntity();
        value.setId("a".repeat(32));
        value.setStorageBucket("ticket-private");
        value.setStorageKey("ticket/ci/attachments/" + value.getId());
        value.setFileSizeBytes((long) bytes.length);
        value.setFileSha256(DiagnosticText.sha256(bytes));
        return value;
    }
}
