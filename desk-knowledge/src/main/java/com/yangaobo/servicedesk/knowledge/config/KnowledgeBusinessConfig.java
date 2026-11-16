package com.yangaobo.servicedesk.knowledge.config;

import com.yangaobo.servicedesk.api.ticket.client.TicketSupportClient;
import com.yangaobo.servicedesk.api.user.client.UserIdentityClient;
import feign.Retryer;
import io.grpc.Context;

import java.net.SocketTimeoutException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.loadbalancer.FeignBlockingLoadBalancerClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 工单Mapper与身份HTTP装配的业务边界。
 *
 * @author yangaobo
 */
@Configuration
@MapperScan("com.yangaobo.servicedesk.knowledge.mapper")
@EnableFeignClients(clients = {UserIdentityClient.class, TicketSupportClient.class})
@ConditionalOnProperty(name = "desk.knowledge.enabled", havingValue = "true", matchIfMissing = true)
public class KnowledgeBusinessConfig {
    /**
     * JDK HTTP客户端保留401响应正文，不采用会吞掉认证响应的流式URLConnection。
     */
    @Bean
    public feign.Client identityHttpClient(
            LoadBalancerClient loadBalancer, LoadBalancerClientFactory factory) {
        feign.Client transport =
                new feign.http2client.Http2Client(
                        HttpClient.newBuilder()
                                .connectTimeout(Duration.ofSeconds(2))
                                .followRedirects(HttpClient.Redirect.NEVER)
                                .build());
        feign.Client bounded =
                (request, options) -> {
                    var deadline = Context.current().getDeadline();
                    if (deadline == null) return transport.execute(request, options);
                    long remaining = deadline.timeRemaining(TimeUnit.MILLISECONDS) - 100;
                    if (remaining <= 0) throw new SocketTimeoutException("父用例预算已耗尽");
                    return transport.execute(
                            request,
                            new feign.Request.Options(
                                    Math.min(options.connectTimeoutMillis(), remaining),
                                    TimeUnit.MILLISECONDS,
                                    Math.min(options.readTimeoutMillis(), remaining),
                                    TimeUnit.MILLISECONDS,
                                    options.isFollowRedirects()));
                };
        return new FeignBlockingLoadBalancerClient(bounded, loadBalancer, factory);
    }

    /**
     * 默认不自动重试身份依赖，避免突破整体预算。
     */
    @Bean
    public Retryer identityRetryer() {
        return Retryer.NEVER_RETRY;
    }
}
