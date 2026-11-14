package com.yangaobo.servicedesk.knowledge.component;

import com.yangaobo.servicedesk.api.ai.v1.IndexJobServiceGrpc;
import com.yangaobo.servicedesk.api.ai.v1.IndexStatus;
import com.yangaobo.servicedesk.api.ai.v1.IndexStatusRequest;
import com.yangaobo.servicedesk.common.base.CommonErrorCode;
import com.yangaobo.servicedesk.common.exception.BusinessException;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.MetadataUtils;
import java.io.File;
import java.util.concurrent.TimeUnit;

/**
 * knowledge仅通过TLS查询AI索引事实，不存在提交或取消旁路。
 *
 * @author yangaobo
 */
@org.springframework.stereotype.Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "desk.knowledge.enabled", havingValue = "true", matchIfMissing = true)
public class AiIndexClient implements AutoCloseable {
    private final io.grpc.ManagedChannel channel;
    private final IndexJobServiceGrpc.IndexJobServiceBlockingStub stub;
    private final io.grpc.ClientInterceptor authentication;
    /** 客户端证书与服务凭据由knowledge独立配置，不复用浏览器身份。 */
    public AiIndexClient(@org.springframework.beans.factory.annotation.Value("${desk.ai.rpc.target:localhost:26205}") String target,
            @org.springframework.beans.factory.annotation.Value("${desk.support.rpc.ca-file}") String ca,
            @org.springframework.beans.factory.annotation.Value("${desk.support.rpc.certificate-file}") String cert,
            @org.springframework.beans.factory.annotation.Value("${desk.support.rpc.private-key-file}") String key,
            @org.springframework.beans.factory.annotation.Value("${desk.ai.rpc.token}") String token) throws Exception {
        if(token.length()<32)throw new IllegalArgumentException("AI查询凭据缺失");
        channel=NettyChannelBuilder.forTarget(target).sslContext(GrpcSslContexts.forClient().trustManager(new File(ca))
            .keyManager(new File(cert),new File(key)).build()).disableRetry().maxInboundMessageSize(4194304).build();
        Metadata headers=new Metadata();headers.put(Metadata.Key.of("authorization",Metadata.ASCII_STRING_MARSHALLER),"Bearer "+token);
        authentication=MetadataUtils.newAttachHeadersInterceptor(headers);
        stub=IndexJobServiceGrpc.newBlockingStub(channel).withInterceptors(authentication);
    }
    /** null仅表示可信NOT_FOUND，不表示没有在途消息。 */
    public IndexStatus status(String jobId) {
        try {return stub.withDeadlineAfter(2900,TimeUnit.MILLISECONDS).getStatus(IndexStatusRequest.newBuilder().setJobId(jobId).build());}
        catch(StatusRuntimeException failure) {
            if(failure.getStatus().getCode()==Status.Code.NOT_FOUND)return null;
            throw new BusinessException(failure.getStatus().getCode()==Status.Code.DEADLINE_EXCEEDED?CommonErrorCode.DEPENDENCY_TIMEOUT:CommonErrorCode.SERVICE_UNAVAILABLE);
        }
    }
    /** 核对历史实际引用关系，不让任意generationId扩大原件范围。 */
    public com.yangaobo.servicedesk.api.knowledge.v1.SourceQuery resolve(com.yangaobo.servicedesk.api.knowledge.v1.SourceQuery query) {
        try {return com.yangaobo.servicedesk.api.ai.v1.EvidenceContextServiceGrpc.newBlockingStub(channel)
            .withInterceptors(authentication)
            .withDeadlineAfter(2900,TimeUnit.MILLISECONDS).resolveContext(com.yangaobo.servicedesk.api.ai.v1.EvidenceContextRequest.newBuilder()
                .setActor(query.getActor()).setGenerationId(query.getGenerationId()).setSourceUsageId(query.getSourceUsageId()).build());}
        catch(StatusRuntimeException failure){throw new BusinessException(failure.getStatus().getCode()==Status.Code.PERMISSION_DENIED || failure.getStatus().getCode()==Status.Code.NOT_FOUND ? CommonErrorCode.FORBIDDEN:CommonErrorCode.SERVICE_UNAVAILABLE);}
    }
    /** 应用关闭有界排空，不残留客户端线程。 */
    @Override
    @jakarta.annotation.PreDestroy
    public void close() {channel.shutdownNow();}
}
