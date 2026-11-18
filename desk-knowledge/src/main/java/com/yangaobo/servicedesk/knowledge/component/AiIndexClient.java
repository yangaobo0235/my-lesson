package com.yangaobo.servicedesk.knowledge.component;

import com.yangaobo.servicedesk.api.ai.v1.EvidenceContextRequest;
import com.yangaobo.servicedesk.api.ai.v1.EvidenceContextServiceGrpc;
import com.yangaobo.servicedesk.api.ai.v1.IndexJobServiceGrpc;
import com.yangaobo.servicedesk.api.ai.v1.IndexStatus;
import com.yangaobo.servicedesk.api.ai.v1.IndexStatusRequest;
import com.yangaobo.servicedesk.api.knowledge.v1.SourceQuery;
import com.yangaobo.servicedesk.api.rpc.RemoteRpcErrors;
import com.yangaobo.servicedesk.api.rpc.RpcContract;
import com.yangaobo.servicedesk.common.base.CommonErrorCode;
import com.yangaobo.servicedesk.common.exception.BusinessException;
import io.grpc.ClientInterceptor;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.MetadataUtils;
import jakarta.annotation.PreDestroy;

import java.io.File;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * knowledge仅通过TLS查询AI索引事实，不存在提交或取消旁路。
 *
 * @author yangaobo
 */
@Component
@ConditionalOnProperty(name = "desk.knowledge.enabled", havingValue = "true", matchIfMissing = true)
public class AiIndexClient implements AutoCloseable {
    private final ManagedChannel channel;
    private final IndexJobServiceGrpc.IndexJobServiceBlockingStub stub;
    private final ClientInterceptor authentication;

    /**
     * 客户端证书与服务凭据由knowledge独立配置，不复用浏览器身份。
     */
    public AiIndexClient(
            @Value("${desk.ai.rpc.target:localhost:26205}") String target,
            @Value("${desk.support.rpc.ca-file}") String ca,
            @Value("${desk.support.rpc.certificate-file}") String cert,
            @Value("${desk.support.rpc.private-key-file}") String key,
            @Value("${desk.ai.rpc.token}") String token)
            throws Exception {
        if (token.length() < 32) throw new IllegalArgumentException("AI查询凭据缺失");
        channel =
                NettyChannelBuilder.forTarget(target)
                        .sslContext(
                                GrpcSslContexts.forClient()
                                        .trustManager(new File(ca))
                                        .keyManager(new File(cert), new File(key))
                                        .build())
                        .disableRetry()
                        .maxInboundMessageSize(4194304)
                        .build();
        Metadata headers = new Metadata();
        headers.put(
                Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER),
                "Bearer " + token);
        authentication = MetadataUtils.newAttachHeadersInterceptor(headers);
        stub = IndexJobServiceGrpc.newBlockingStub(channel).withInterceptors(authentication);
    }

    /**
     * null仅表示可信NOT_FOUND，不表示没有在途消息。
     */
    public IndexStatus status(String jobId) {
        try {
            return stub.withDeadlineAfter(
                            RpcContract.remainingMillis("GetStatus"), TimeUnit.MILLISECONDS)
                    .getStatus(IndexStatusRequest.newBuilder().setJobId(jobId).build());
        } catch (StatusRuntimeException failure) {
            var error = RemoteRpcErrors.translate(failure, Map.of(1002, CommonErrorCode.NOT_FOUND));
            if (error == CommonErrorCode.NOT_FOUND) return null;
            throw new BusinessException(error);
        }
    }

    /**
     * 核对历史实际引用关系，不让任意runId扩大原件范围。
     */
    public SourceQuery resolve(SourceQuery query) {
        try {
            return EvidenceContextServiceGrpc.newBlockingStub(channel)
                    .withInterceptors(authentication)
                    .withDeadlineAfter(
                            RpcContract.remainingMillis("ResolveContext"), TimeUnit.MILLISECONDS)
                    .resolveContext(
                            EvidenceContextRequest.newBuilder()
                                    .setActor(query.getActor())
                                    .setRunId(query.getRunId())
                                    .setSourceUsageId(query.getSourceUsageId())
                                    .build());
        } catch (StatusRuntimeException failure) {
            var error =
                    RemoteRpcErrors.translate(
                            failure,
                            Map.of(
                                    1002,
                                    CommonErrorCode.FORBIDDEN,
                                    1003,
                                    CommonErrorCode.FORBIDDEN,
                                    1004,
                                    CommonErrorCode.CONFLICT));
            throw new BusinessException(error);
        }
    }

    /**
     * 应用关闭有界排空，不残留客户端线程。
     */
    @Override
    @PreDestroy
    public void close() {
        channel.shutdownNow();
    }
}
