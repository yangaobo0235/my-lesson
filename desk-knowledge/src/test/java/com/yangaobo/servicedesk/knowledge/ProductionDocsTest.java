package com.yangaobo.servicedesk.knowledge;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 验证生产环境不暴露原生文档及界面，仍保留健康探针。
 *
 * @author yangaobo
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"prod", "test"})
class ProductionDocsTest {

    /**
     * 当前隔离应用端口。
     */
    @LocalServerPort
    private int port;

    /**
     * 检查实际生产Profile下文档关闭，避免只验证配置文件存在。
     *
     * @throws IOException 请求失败
     * @throws InterruptedException 等待中断
     */
    @Test
    void closesDocumentationWithoutClosingHealth() throws IOException, InterruptedException {
        try (HttpClient client = HttpClient.newHttpClient()) {
            for (String path : new String[]{"/v3/api-docs", "/swagger-ui.html", "/swagger-ui/index.html"}) {
                HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                        .timeout(Duration.ofSeconds(10)).GET().build();
                assertEquals(404, client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode(), path);
            }
            HttpRequest health = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/actuator/health"))
                    .timeout(Duration.ofSeconds(10)).GET().build();
            assertEquals(200, client.send(health, HttpResponse.BodyHandlers.discarding()).statusCode());
        }
    }
}
