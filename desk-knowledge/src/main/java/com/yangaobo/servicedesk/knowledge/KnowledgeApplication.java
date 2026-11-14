package com.yangaobo.servicedesk.knowledge;

import com.yangaobo.servicedesk.knowledge.swagger.SwaggerConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * 启动资料与知识服务，仅扫描所属应用包。
 *
 * @author yangaobo
 */
@SpringBootApplication
@Import(SwaggerConfig.class)
public class KnowledgeApplication {

    /**
     * 启动当前服务并由应用配置选择基础环境。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        // Sentinel在Spring配置加载前初始化，显式设置其原生日志路径以避免写入用户HOME。
        if (System.getProperty("csp.sentinel.log.dir") == null) {
            String directory = System.getenv().getOrDefault("DESK_LOG_DIRECTORY", "./.local/runtime/logs");
            System.setProperty("csp.sentinel.log.dir", directory + "/desk-knowledge/sentinel");
        }
        SpringApplication.run(KnowledgeApplication.class, args);
    }
}
