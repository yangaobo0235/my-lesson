package com.yangaobo.servicedesk.ticket.config;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 绑定本服务执行器配置，不持有Admin数据库账号。
 *
 * @author yangaobo
 */
@Data
@ConfigurationProperties("desk.xxl-job")
public class XxlJobProperties implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Admin调度根地址。
     */
    private String adminAddresses;

    /**
     * 执行器通信令牌，不进入日志。
     */
    @ToString.Exclude
    private String accessToken;

    /**
     * 按环境隔离的执行器名。
     */
    private String appName;

    /**
     * 可由服务器回调的执行器地址。
     */
    private String address;

    /**
     * 本机可路由地址。
     */
    private String ip;

    /**
     * 执行器私网端口。
     */
    private int port;

    /**
     * 任务滚动日志目录。
     */
    private String logPath;

    /**
     * Admin HTTP调用的有限超时秒数。
     */
    private int timeoutSeconds = 3;

    /**
     * 日志保留天数。
     */
    private int logRetentionDays;
}
