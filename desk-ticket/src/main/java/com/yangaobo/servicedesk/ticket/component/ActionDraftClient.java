package com.yangaobo.servicedesk.ticket.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.yangaobo.servicedesk.api.ai.v1.ActionDraftServiceGrpc;
import com.yangaobo.servicedesk.api.ai.v1.ValidateDraftRequest;
import com.yangaobo.servicedesk.api.common.v1.ActorRef;
import com.yangaobo.servicedesk.api.common.v1.RpcErrorDetail;
import com.yangaobo.servicedesk.api.user.dto.IdentityDTO;
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
 * 人工采用草稿前的AI只读核验；纯人工链不连接AI即可独立运行。
 *
 * @author yangaobo
 */
@org.springframework.stereotype.Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "desk.ticket.enabled", havingValue = "true", matchIfMissing = true)
public class ActionDraftClient {
    private final String target;
    private final String ca;
    private final String certificate;
    private final String key;
    private final String token;
    private final ObjectMapper json;
    private volatile io.grpc.ManagedChannel channel;
    /** 延迟建立连接；AI缺失只拒绝草稿采用，不影响人工提单处理。 */
    public ActionDraftClient(ObjectMapper json,
            @org.springframework.beans.factory.annotation.Value("${desk.ai.rpc.target:localhost:26205}") String target,
            @org.springframework.beans.factory.annotation.Value("${desk.ai.rpc.ca-file:}") String ca,
            @org.springframework.beans.factory.annotation.Value("${desk.ai.rpc.certificate-file:}") String certificate,
            @org.springframework.beans.factory.annotation.Value("${desk.ai.rpc.private-key-file:}") String key,
            @org.springframework.beans.factory.annotation.Value("${desk.ai.rpc.token:}") String token){
        this.json=json;this.target=target;this.ca=ca;this.certificate=certificate;this.key=key;this.token=token;
    }
    /** 网络调用在工单事务外，返回最小交接事实，不返回私有聊天或原Prompt。 */
    public JsonNode validate(IdentityDTO actor,String draftId,String purpose,String customerId,String serviceItemId,
            String deviceId,String ticketId,Long ticketVersion) {
        if(draftId==null)return null;
        var request=ValidateDraftRequest.newBuilder().setActor(ActorRef.newBuilder().setOwnerId(actor.getOwnerId())
            .setSessionRef(actor.getSessionRef()).setAuthVersion(actor.getAuthVersion())).setDraftId(draftId).setPurpose(purpose).setCustomerId(customerId);
        if(serviceItemId!=null)request.setServiceItemId(serviceItemId);if(deviceId!=null)request.setDeviceId(deviceId);
        if(ticketId!=null)request.setTicketId(ticketId);if(ticketVersion!=null)request.setTicketVersion(ticketVersion);
        Metadata headers=new Metadata();headers.put(Metadata.Key.of("authorization",Metadata.ASCII_STRING_MARSHALLER),"Bearer "+token);
        try(var entry=com.alibaba.csp.sentinel.SphU.entry("ticket:ai:validate-draft")){
            try {
                var response=ActionDraftServiceGrpc.newBlockingStub(channel()).withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers))
                    .withDeadlineAfter(2900,TimeUnit.MILLISECONDS).validateDraft(request.build());
                if(!draftId.equals(response.getId()) || !purpose.equals(response.getPurpose()))throw new BusinessException(CommonErrorCode.UPSTREAM_PROTOCOL_ERROR);
                return json.createObjectNode().put("schema_version",1).put("source_generation_id",response.getSourceGenerationId())
                    .put("confirmed_symptom",response.getSymptom()).put("confirmed_attempted_actions",response.getAttemptedActions());
            }catch(StatusRuntimeException failure){
                var status=io.grpc.protobuf.StatusProto.fromThrowable(failure);
                if(status!=null && status.getCode()==failure.getStatus().getCode().value())for(var packed:status.getDetailsList())try{
                    if(packed.is(RpcErrorDetail.class)){
                        var detail=packed.unpack(RpcErrorDetail.class);
                        if(detail.getCode()==5201 && failure.getStatus().getCode()==Status.Code.FAILED_PRECONDITION)throw new BusinessException(RemoteDraftError.DRAFT_EXPIRED);
                        if(detail.getCode()==1004 && failure.getStatus().getCode()==Status.Code.FAILED_PRECONDITION)throw new BusinessException(CommonErrorCode.CONFLICT);
                        if(detail.getCode()==1003 && failure.getStatus().getCode()==Status.Code.PERMISSION_DENIED)throw new BusinessException(CommonErrorCode.FORBIDDEN);
                    }
                }catch(com.google.protobuf.InvalidProtocolBufferException malformed){throw new BusinessException(CommonErrorCode.UPSTREAM_PROTOCOL_ERROR);}
                com.alibaba.csp.sentinel.Tracer.traceEntry(failure,entry);
                throw new BusinessException(failure.getStatus().getCode()==Status.Code.DEADLINE_EXCEEDED?CommonErrorCode.DEPENDENCY_TIMEOUT:CommonErrorCode.SERVICE_UNAVAILABLE);
            }
        }catch(com.alibaba.csp.sentinel.slots.block.BlockException limited){throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE);}
    }
    /** 通道只在首次真实草稿消费者调用时创建，后续按进程复用。 */
    private synchronized io.grpc.ManagedChannel channel() {
        if(channel==null){
            if(token.length()<32 || ca.isBlank() || certificate.isBlank() || key.isBlank())throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE);
            try{channel=NettyChannelBuilder.forTarget(target).sslContext(GrpcSslContexts.forClient().trustManager(new File(ca))
                .keyManager(new File(certificate),new File(key)).build()).disableRetry().maxInboundMessageSize(4194304).build();}
            catch(Exception failure){throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE);}
        }
        return channel;
    }
    /** 应用关闭收回懒通道，未使用AI时无资源需要释放。 */
    @jakarta.annotation.PreDestroy
    public synchronized void close(){if(channel!=null)channel.shutdownNow();}
}
