package com.yangaobo.servicedesk.ticket;

import com.yangaobo.servicedesk.ticket.swagger.SwaggerConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * 启动设备与工单服务，仅扫描所属应用包。
 *
 * @author yangaobo
 */
@SpringBootApplication
@Import(SwaggerConfig.class)
public class TicketApplication {

    /**
     * 启动当前服务并由应用配置选择基础环境。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        // Sentinel在Spring配置加载前初始化，显式设置其原生日志路径以避免写入用户HOME。
        if (System.getProperty("csp.sentinel.log.dir") == null) {
            String directory =
                    System.getenv().getOrDefault("DESK_LOG_DIRECTORY", "./.local/runtime/logs");
            System.setProperty("csp.sentinel.log.dir", directory + "/desk-ticket/sentinel");
        }
        SpringApplication.run(TicketApplication.class, args);
    }
}
