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
        SpringApplication.run(TicketApplication.class, args);
    }
}
