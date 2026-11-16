package com.yangaobo.servicedesk.ticket.component;

import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.Tracer;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangaobo.servicedesk.api.ai.v1.ActionDraftServiceGrpc;
import com.yangaobo.servicedesk.api.ai.v1.ValidateDraftRequest;
import com.yangaobo.servicedesk.api.common.v1.ActorRef;
import com.yangaobo.servicedesk.api.rpc.RemoteRpcErrors;
import com.yangaobo.servicedesk.api.rpc.RpcContract;
import com.yangaobo.servicedesk.api.user.dto.IdentityDTO;
import com.yangaobo.servicedesk.common.base.CommonErrorCode;
import com.yangaobo.servicedesk.common.exception.BusinessException;
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
 * 人工采用草稿前的AI只读核验；纯人工链不连接AI即可独立运行。
 *
 * @author yangaobo
 */
@Component
@ConditionalOnProperty(name = "desk.ticket.enabled", havingValue = "true", matchIfMissing = true)
public class ActionDraftClient {
    private final String target;
    private final String ca;
    private final String certificate;
    private final String key;
    private final String token;
    private final ObjectMapper json;
    private volatile ManagedChannel channel;

    /**
     * 延迟建立连接；AI缺失只拒绝草稿采用，不影响人工提单处理。
     */
    public ActionDraftClient(
            ObjectMapper json,
            @Value("${desk.ai.rpc.target:localhost:26205}") String target,
            @Value("${desk.ai.rpc.ca-file:}") String ca,
            @Value("${desk.ai.rpc.certificate-file:}") String certificate,
            @Value("${desk.ai.rpc.private-key-file:}") String key,
            @Value("${desk.ai.rpc.token:}") String token) {
        this.json = json;
        this.target = target;
        this.ca = ca;
        this.certificate = certificate;
        this.key = key;
        this.token = token;
    }

    /**
     * 网络调用在工单事务外，返回最小交接事实，不返回私有聊天或原Prompt。
     */
    public JsonNode validate(
            IdentityDTO actor,
            String draftId,
            String purpose,
            String customerId,
            String serviceItemId,
            String deviceId,
            String ticketId,
            Long ticketVersion) {
        if (draftId == null) return null;
        var request =
                ValidateDraftRequest.newBuilder()
                        .setActor(
                                ActorRef.newBuilder()
                                        .setOwnerId(actor.getOwnerId())
                                        .setSessionRef(actor.getSessionRef())
                                        .setAuthVersion(actor.getAuthVersion()))
                        .setDraftId(draftId)
                        .setPurpose(purpose)
                        .setCustomerId(customerId);
        if (serviceItemId != null) request.setServiceItemId(serviceItemId);
        if (deviceId != null) request.setDeviceId(deviceId);
        if (ticketId != null) request.setTicketId(ticketId);
        if (ticketVersion != null) request.setTicketVersion(ticketVersion);
        Metadata headers = new Metadata();
        headers.put(
                Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER),
                "Bearer " + token);
        try (var entry = SphU.entry("ticket:ai:validate-draft")) {
            try {
                var response =
                        ActionDraftServiceGrpc.newBlockingStub(channel())
                                .withInterceptors(
                                        MetadataUtils.newAttachHeadersInterceptor(headers))
                                .withDeadlineAfter(
                                        RpcContract.remainingMillis("ValidateDraft"),
                                        TimeUnit.MILLISECONDS)
                                .validateDraft(request.build());
                if (!draftId.equals(response.getId()) || !purpose.equals(response.getPurpose()))
                    throw new BusinessException(CommonErrorCode.UPSTREAM_PROTOCOL_ERROR);
                return json.createObjectNode()
                        .put("schema_version", 1)
                        .put("source_generation_id", response.getSourceGenerationId())
                        .put("confirmed_symptom", response.getSymptom())
                        .put("confirmed_attempted_actions", response.getAttemptedActions());
            } catch (StatusRuntimeException failure) {
                var error =
                        RemoteRpcErrors.translate(
                                failure,
                                Map.of(
                                        5201,
                                        RemoteDraftError.DRAFT_EXPIRED,
                                        1004,
                                        CommonErrorCode.CONFLICT,
                                        1003,
                                        CommonErrorCode.FORBIDDEN,
                                        1002,
                                        CommonErrorCode.NOT_FOUND,
                                        1001,
                                        CommonErrorCode.INVALID_REQUEST));
                if (error == CommonErrorCode.SERVICE_UNAVAILABLE
                        || error == CommonErrorCode.DEPENDENCY_TIMEOUT
                        || error == CommonErrorCode.UPSTREAM_PROTOCOL_ERROR)
                    Tracer.traceEntry(failure, entry);
                throw new BusinessException(error);
            }
        } catch (BlockException limited) {
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE);
        }
    }

    /**
     * 通道只在首次真实草稿消费者调用时创建，后续按进程复用。
     */
    private synchronized ManagedChannel channel() {
        if (channel == null) {
            if (token.length() < 32 || ca.isBlank() || certificate.isBlank() || key.isBlank())
                throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE);
            try {
                channel =
                        NettyChannelBuilder.forTarget(target)
                                .sslContext(
                                        GrpcSslContexts.forClient()
                                                .trustManager(new File(ca))
                                                .keyManager(new File(certificate), new File(key))
                                                .build())
                                .disableRetry()
                                .maxInboundMessageSize(4194304)
                                .build();
            } catch (Exception failure) {
                throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE);
            }
        }
        return channel;
    }

    /**
     * 应用关闭收回懒通道，未使用AI时无资源需要释放。
     */
    @PreDestroy
    public synchronized void close() {
        if (channel != null) channel.shutdownNow();
    }
}
