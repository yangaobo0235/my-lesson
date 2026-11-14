package com.yangaobo.servicedesk.ticket.config;

import com.yangaobo.servicedesk.api.user.client.UserIdentityClient;

import feign.Retryer;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 工单Mapper与身份HTTP装配的业务边界。
 *
 * @author yangaobo
 */
@Configuration
@MapperScan("com.yangaobo.servicedesk.ticket.mapper")
@EnableFeignClients(clients = UserIdentityClient.class)
@ConditionalOnProperty(name = "desk.ticket.enabled", havingValue = "true", matchIfMissing = true)
public class TicketBusinessConfig {
    /** JDK HTTP客户端保留401响应正文，不采用会吞掉认证响应的流式URLConnection。 */
    @Bean
    public feign.Client identityHttpClient(
            org.springframework.cloud.client.loadbalancer.LoadBalancerClient loadBalancer,
            org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory factory) {
        feign.Client transport =
                new feign.http2client.Http2Client(
                        java.net.http.HttpClient.newBuilder()
                                .connectTimeout(java.time.Duration.ofSeconds(2))
                                .followRedirects(java.net.http.HttpClient.Redirect.NEVER)
                                .build());
        return new org.springframework.cloud.openfeign.loadbalancer.FeignBlockingLoadBalancerClient(
                transport, loadBalancer, factory);
    }

    /** 默认不自动重试身份依赖，避免突破整体预算。 */
    @Bean
    public Retryer identityRetryer() {
        return Retryer.NEVER_RETRY;
    }
}
