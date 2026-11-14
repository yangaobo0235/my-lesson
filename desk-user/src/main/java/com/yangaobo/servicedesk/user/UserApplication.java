package com.yangaobo.servicedesk.user;

import com.yangaobo.servicedesk.user.swagger.SwaggerConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * 启动身份与组织服务，仅扫描所属应用包。
 *
 * @author yangaobo
 */
@SpringBootApplication
@Import(SwaggerConfig.class)
public class UserApplication {

    /**
     * 启动当前服务并由应用配置选择基础环境。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        // Sentinel在Spring配置加载前初始化，显式设置其原生日志路径以避免写入用户HOME。
        if (System.getProperty("csp.sentinel.log.dir") == null) {
            String directory = System.getenv().getOrDefault("DESK_LOG_DIRECTORY", "./.local/runtime/logs");
            System.setProperty("csp.sentinel.log.dir", directory + "/desk-user/sentinel");
        }
        SpringApplication.run(UserApplication.class, args);
    }
}
