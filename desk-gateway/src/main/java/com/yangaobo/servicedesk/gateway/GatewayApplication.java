package com.yangaobo.servicedesk.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 启动响应式统一网关，不扫描业务应用或引入MVC运行栈。
 *
 * @author yangaobo
 */
@SpringBootApplication
public class GatewayApplication {

    /**
     * 启动网关并加载所属环境路由。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        // Sentinel在Spring配置加载前初始化，显式设置其原生日志路径以避免写入用户HOME。
        if (System.getProperty("csp.sentinel.log.dir") == null) {
            String directory =
                    System.getenv().getOrDefault("DESK_LOG_DIRECTORY", "./.local/runtime/logs");
            System.setProperty("csp.sentinel.log.dir", directory + "/desk-gateway/sentinel");
        }
        SpringApplication.run(GatewayApplication.class, args);
    }
}
