package com.yangaobo.servicedesk.ticket.config;

import com.yangaobo.servicedesk.ticket.rpc.DomainRpcInterceptor;
import com.yangaobo.servicedesk.ticket.rpc.DiagnosticAttachmentRpcService;
import com.yangaobo.servicedesk.ticket.rpc.SupportAccessRpcService;
import com.yangaobo.servicedesk.ticket.security.DomainServiceCredentials;
import io.grpc.Server;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.ClientAuth;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 独立领域RPC监听，开发与生产都要求CA及双向TLS，不回退明文。
 *
 * @author yangaobo
 */
@Configuration
@ConditionalOnProperty(name = "desk.ticket.enabled", havingValue = "true", matchIfMissing = true)
public class DomainRpcConfig {
    /**
     * 启动前校验证书文件，关闭时有界排空在途调用。
     */
    @Bean
    @ConditionalOnProperty(
            name = "desk.support.rpc.enabled",
            havingValue = "true",
            matchIfMissing = true)
    public SmartLifecycle domainRpcLifecycle(
            SupportAccessRpcService supportAccessRpcService,
            DiagnosticAttachmentRpcService diagnosticAttachmentRpcService,
            DomainServiceCredentials credentials,
            Propagator propagator,
            Tracer tracer,
            @Value("${desk.support.rpc.host:127.0.0.1}") String host,
            @Value("${desk.support.rpc.port:26203}") int port,
            @Value("${desk.support.rpc.ca-file}") String ca,
            @Value("${desk.support.rpc.certificate-file}") String certificate,
            @Value("${desk.support.rpc.private-key-file}") String key)
            throws Exception {
        var executor = Executors.newVirtualThreadPerTaskExecutor();
        Server server =
                NettyServerBuilder.forAddress(new InetSocketAddress(host, port))
                        .sslContext(
                                GrpcSslContexts.forServer(required(certificate), required(key))
                                        .trustManager(required(ca))
                                        .clientAuth(ClientAuth.REQUIRE)
                                        .build())
                        .executor(executor)
                        .maxInboundMessageSize(4 * 1024 * 1024)
                        .maxConcurrentCallsPerConnection(32)
                        .addService(supportAccessRpcService)
                        .addService(diagnosticAttachmentRpcService)
                        .intercept(new DomainRpcInterceptor(credentials, propagator, tracer))
                        .build();
        return new RpcLifecycle(server, executor);
    }

    /**
     * 空配置及不存在的TLS文件必须阻止启动。
     */
    private File required(String path) {
        File file = new File(path);
        if (path.isBlank() || !file.isFile())
            throw new IllegalArgumentException("领域RPC必须配置可读TLS文件");
        return file;
    }

    /**
     * 持有独立监听的启动与关闭责任。 @author yangaobo
     */
    private static final class RpcLifecycle implements SmartLifecycle {
        /**
         * 本生命周期唯一管理的服务。
         */
        private final Server server;

        /**
         * 本监听拥有的虚拟线程执行器。
         */
        private final ExecutorService executor;

        /**
         * 是否成功启动。
         */
        private volatile boolean running;

        /**
         * 保存已经校验的服务器。
         */
        private RpcLifecycle(Server server, ExecutorService executor) {
            this.server = server;
            this.executor = executor;
        }

        /**
         * 启动失败阻止应用报告可用。
         */
        @Override
        public void start() {
            try {
                server.start();
                running = true;
            } catch (IOException failure) {
                throw new IllegalStateException("领域RPC监听启动失败", failure);
            }
        }

        /**
         * 最多等待五秒，之后取消剩余调用。
         */
        @Override
        public void stop() {
            server.shutdown();
            try {
                if (!server.awaitTermination(5, TimeUnit.SECONDS)) server.shutdownNow();
            } catch (InterruptedException failure) {
                server.shutdownNow();
                Thread.currentThread().interrupt();
            }
            executor.shutdownNow();
            running = false;
        }

        /**
         * 返回监听生命周期状态。
         */
        @Override
        public boolean isRunning() {
            return running;
        }
    }
}
