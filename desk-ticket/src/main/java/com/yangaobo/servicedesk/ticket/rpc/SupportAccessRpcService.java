package com.yangaobo.servicedesk.ticket.rpc;

import com.yangaobo.servicedesk.api.common.v1.ActorRef;
import com.yangaobo.servicedesk.api.ticket.dto.SupportContextDTO;
import com.yangaobo.servicedesk.api.ticket.dto.SupportEnvironment;
import com.yangaobo.servicedesk.api.ticket.form.SupportAccessForm;
import com.yangaobo.servicedesk.api.ticket.v1.ContextRequest;
import com.yangaobo.servicedesk.api.ticket.v1.Environment;
import com.yangaobo.servicedesk.api.ticket.v1.SupportAccessServiceGrpc;
import com.yangaobo.servicedesk.api.ticket.v1.SupportContext;
import com.yangaobo.servicedesk.api.ticket.v1.TicketEvent;
import com.yangaobo.servicedesk.api.ticket.v1.TicketEvents;
import com.yangaobo.servicedesk.api.ticket.v1.TicketEventsRequest;
import com.yangaobo.servicedesk.api.ticket.v1.TicketRequest;
import com.yangaobo.servicedesk.api.ticket.v1.VerifyTicketRequest;
import com.yangaobo.servicedesk.api.ticket.v1.DraftReceiptRequest;
import com.yangaobo.servicedesk.api.ticket.v1.DraftReceipt;
import com.yangaobo.servicedesk.api.ticket.v1.DraftReconcileRequest;
import com.yangaobo.servicedesk.api.ticket.v1.DraftReconcileResult;
import com.yangaobo.servicedesk.ticket.model.vo.ticket.TicketActionReceiptVO;
import com.yangaobo.servicedesk.ticket.service.IActionReceiptService;
import com.yangaobo.servicedesk.common.base.CommonErrorCode;
import com.yangaobo.servicedesk.common.exception.BusinessException;
import com.yangaobo.servicedesk.ticket.component.IdentityVerificationClient;
import com.yangaobo.servicedesk.ticket.mapper.TicketEventMapper;
import com.yangaobo.servicedesk.ticket.service.ISupportAccessService;
import io.grpc.stub.StreamObserver;

import java.time.ZoneOffset;
import java.util.HashSet;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * AI只读领域请求适配同一支持范围用例，不提供写工单旁路。
 *
 * @author yangaobo
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "desk.ticket.enabled", havingValue = "true", matchIfMissing = true)
public class SupportAccessRpcService extends SupportAccessServiceGrpc.SupportAccessServiceImplBase {
    private final ISupportAccessService supportAccessService;
    private final IdentityVerificationClient identities;
    private final TicketEventMapper events;
    private final IActionReceiptService receipts;

    /**
     * 以原账号和当前工单读取权查询正式回执，不返回私有对话或推断执行结果。
     */
    @Override
    public void readDraftReceipt(DraftReceiptRequest request, StreamObserver<DraftReceipt> observer) {
        RpcResponses.respond(observer, () -> {
            if (!request.getDraftRootId().matches("[0-9a-f]{32}"))
                throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
            var actor = identities.verifySession(form(request.getActor()));
            var value = receipts.byDraft(actor, request.getDraftRootId());
            return draftReceipt(value);
        });
    }

    /**
     * 独立服务认证已由拦截器校验；只返回精确拥有者及根的最小已提交事实。
     */
    @Override
    public void reconcileDraftReceipt(DraftReconcileRequest request, StreamObserver<DraftReconcileResult> observer) {
        RpcResponses.respond(observer, () -> {
            if (!request.getOwnerId().matches("[0-9a-f]{32}") || !request.getDraftRootId().matches("[0-9a-f]{32}"))
                throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
            var value = receipts.reconcileDraft(request.getOwnerId(), request.getDraftRootId());
            var result = DraftReconcileResult.newBuilder();
            if (value != null) result.setReceipt(draftReceipt(value));
            return result.build();
        });
    }

    /**
     * 只序列化采用身份和结果，不向系统对账提供工单正文或用户权限。
     */
    private DraftReceipt draftReceipt(TicketActionReceiptVO value) {
        var result = DraftReceipt.newBuilder().setReceiptId(value.getReceiptId())
                .setDraftRootId(value.getSourceDraftRootId()).setDraftId(value.getSourceDraftId())
                .setActionType(value.getActionType()).setTicketId(value.getTicketId())
                .setResultCode(value.getResultCode()).setTicketRowVersion(value.getTicketRowVersion());
        if (value.getBookingId() != null) result.setBookingId(value.getBookingId());
        return result.build();
    }

    /**
     * 客户自助按固定项目与可选设备取得当前资格。
     */
    @Override
    public void checkContext(ContextRequest request, StreamObserver<SupportContext> observer) {
        RpcResponses.respond(
                observer,
                () -> {
                    var form = form(request.getActor());
                    form.setPurpose(request.getPurpose());
                    form.setServiceItemId(request.hasServiceItemId() ? request.getServiceItemId() : null);
                    form.setDeviceId(request.hasDeviceId() ? request.getDeviceId() : null);
                    form.setEnvironment(environment(request.getEnvironment()));
                    if (request.hasTicketId()) {
                        if (!"HISTORY".equals(request.getPurpose()) || !request.getTicketId().matches("[0-9a-f]{32}"))
                            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
                        form.setTicketId(request.getTicketId());
                    }
                    return context(supportAccessService.check(form));
                });
    }

    /**
     * 技术助手固定当前有权工单，客户端不能覆盖客户或环境。
     */
    @Override
    public void readDiagnosisContext(TicketRequest request, StreamObserver<SupportContext> observer) {
        RpcResponses.respond(
                observer, () -> context(ticket(request.getActor(), request.getTicketId())));
    }

    /**
     * 仅技术身份读取同一工单的有界事件，不提供跨工单搜索。
     */
    @Override
    public void readTicketEvents(
            TicketEventsRequest request, StreamObserver<TicketEvents> observer) {
        RpcResponses.respond(observer, () -> readEvents(request));
    }

    /**
     * 核验当前访问权和全部实际事件；返回环境版本，由执行用例判断过时。
     */
    @Override
    public void verifyTicketAccess(
            VerifyTicketRequest request, StreamObserver<SupportContext> observer) {
        RpcResponses.respond(
                observer,
                () -> {
                    var value = ticket(request.getActor(), request.getTicketId());
                    if (!request.getEventIdsList().isEmpty()) {
                        var checked =
                                readEvents(
                                        TicketEventsRequest.newBuilder()
                                                .setActor(request.getActor())
                                                .setTicketId(request.getTicketId())
                                                .addAllEventIds(request.getEventIdsList())
                                                .setLimit(20)
                                                .build());
                        if (checked.getEventsCount()
                                != new HashSet<>(request.getEventIdsList()).size())
                            throw new BusinessException(CommonErrorCode.FORBIDDEN);
                    }
                    return context(value);
                });
    }

    /**
     * 角色与工单范围每次独立核验，读取资格不赋予处理动作权。
     */
    private SupportContextDTO ticket(ActorRef actor, String id) {
        var form = form(actor);
        var identity = identities.verifySession(form);
        if (!identity.getRoles().contains("TECHNICIAN"))
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        form.setPurpose("HISTORY");
        form.setTicketId(id);
        return supportAccessService.check(form);
    }

    /**
     * 查询集合或正序游标二选一，所有事件仍受工单范围约束。
     */
    private TicketEvents readEvents(TicketEventsRequest request) {
        ticket(request.getActor(), request.getTicketId());
        if (request.getLimit() < 1
                || request.getLimit() > 20
                || request.getEventIdsCount() > 20
                || request.getAfterSequence() < 0
                || (request.getEventIdsCount() > 0 && request.hasAfterSequence()))
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        var rows = events.readForDiagnosis(request.getTicketId(), request.getEventIdsList(),
                request.getAfterSequence(), request.getLimit());
        var result = TicketEvents.newBuilder();
        for (var event : rows)
            result.addEvents(
                    TicketEvent.newBuilder()
                            .setId(event.getId())
                            .setSequence(event.getSequenceNumber())
                            .setEventType(event.getEventType())
                            .setVisibility(event.getVisibility())
                            .setContent(event.getContent() == null ? "" : event.getContent())
                            .setCreatedAt(
                                    event.getCreatedAt().toInstant(ZoneOffset.UTC).toString()));
        return result.build();
    }

    /**
     * 原身份引用转换，不接收角色或客户列表。
     */
    private SupportAccessForm form(ActorRef actor) {
        SupportAccessForm result = new SupportAccessForm();
        result.setOwnerId(actor.getOwnerId());
        result.setSessionRef(actor.getSessionRef());
        result.setAuthVersion(actor.getAuthVersion());
        return result;
    }

    /**
     * 可信字段逐项转换，缺失不变为真实环境。
     */
    private SupportEnvironment environment(Environment value) {
        SupportEnvironment result = new SupportEnvironment();
        result.setOsName(value.getOsName().isEmpty() ? null : value.getOsName());
        result.setOsVersion(value.getOsVersion().isEmpty() ? null : value.getOsVersion());
        result.setSoftwareName(value.getSoftwareName().isEmpty() ? null : value.getSoftwareName());
        result.setSoftwareVersion(
                value.getSoftwareVersion().isEmpty() ? null : value.getSoftwareVersion());
        result.setConnectionType(
                value.getConnectionType().isEmpty() ? null : value.getConnectionType());
        result.setAffectedScope(
                value.getAffectedScope().isEmpty() ? null : value.getAffectedScope());
        result.setErrorCode(value.getErrorCode().isEmpty() ? null : value.getErrorCode());
        return result;
    }

    /**
     * 公开环境不含任意可执行属性。
     */
    private Environment environment(SupportEnvironment value) {
        var result = Environment.newBuilder();
        if (value == null) return result.build();
        if (value.getOsName() != null) result.setOsName(value.getOsName());
        if (value.getOsVersion() != null) result.setOsVersion(value.getOsVersion());
        if (value.getSoftwareName() != null) result.setSoftwareName(value.getSoftwareName());
        if (value.getSoftwareVersion() != null)
            result.setSoftwareVersion(value.getSoftwareVersion());
        if (value.getConnectionType() != null) result.setConnectionType(value.getConnectionType());
        if (value.getAffectedScope() != null) result.setAffectedScope(value.getAffectedScope());
        if (value.getErrorCode() != null) result.setErrorCode(value.getErrorCode());
        return result.build();
    }

    /**
     * DTO只在RPC边界转换，不跨服务暴露Entity。
     */
    private SupportContext context(SupportContextDTO value) {
        var result =
                SupportContext.newBuilder()
                        .setCustomerId(value.getCustomerId())
                        .setEnvironment(environment(value.getEnvironment()));
        if (value.getServiceItemId() != null) result.setServiceItemId(value.getServiceItemId());
        if (value.getDeviceId() != null) result.setDeviceId(value.getDeviceId());
        if (value.getProductModelId() != null) result.setProductModelId(value.getProductModelId());
        if (value.getTicketId() != null) result.setTicketId(value.getTicketId());
        if (value.getTicketRowVersion() != null) result.setTicketRowVersion(value.getTicketRowVersion());
        if (value.getDiagnosisContextVersion() != null)
            result.setDiagnosisContextVersion(value.getDiagnosisContextVersion());
        if (value.getTicketEventSequence() != null) result.setTicketEventSequence(value.getTicketEventSequence());
        if (value.getAttemptedActions() != null) result.setAttemptedActions(value.getAttemptedActions());
        if (value.getAssigneeId() != null) result.setAssigneeId(value.getAssigneeId());
        if (value.getSupportScope() != null) result.setSupportScope(value.getSupportScope());
        if (value.getTitle() != null) result.setTitle(value.getTitle());
        if (value.getSymptom() != null) result.setSymptom(value.getSymptom());
        if (value.getStatus() != null) result.setStatus(value.getStatus());
        return result.build();
    }
}
