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
        SpringApplication.run(GatewayApplication.class, args);
    }
}
