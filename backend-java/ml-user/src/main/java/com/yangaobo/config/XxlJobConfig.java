package com.yangaobo.config;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** @author xuxueli 2017-04-28 */
@Slf4j
@Configuration
public class XxlJobConfig {
    @Value("${xxl.job.admin.addresses:${XXL_JOB_ADMIN_ADDRESSES:http://127.0.0.1:9527/xxl-job-admin}}")
    private String adminAddresses;
    @Value("${xxl.job.accessToken:${XXL_JOB_ACCESS_TOKEN:}}")
    private String accessToken;
    @Value("${xxl.job.executor.appName:${XXL_JOB_USER_EXECUTOR_APP_NAME:ml-user-executor}}")
    private String appName;
    @Value("${xxl.job.executor.ip:${XXL_JOB_EXECUTOR_IP:}}")
    private String ip;
    @Value("${xxl.job.executor.port:${XXL_JOB_USER_EXECUTOR_PORT:9998}}")
    private int port;

    @Bean
    public XxlJobSpringExecutor xxlJobExecutor() {
        log.info(">>>>>>>>>>> xxl-job config init.");
        XxlJobSpringExecutor xxlJobSpringExecutor = new XxlJobSpringExecutor();
        xxlJobSpringExecutor.setAdminAddresses(adminAddresses);
        xxlJobSpringExecutor.setAppname(appName);
        xxlJobSpringExecutor.setAddress("");
        xxlJobSpringExecutor.setIp(ip);
        xxlJobSpringExecutor.setPort(port);
        xxlJobSpringExecutor.setAccessToken(accessToken);
        return xxlJobSpringExecutor;
    }
}
